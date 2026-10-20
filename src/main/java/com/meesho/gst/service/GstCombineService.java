package com.meesho.gst.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.meesho.gst.dto.*;
import com.meesho.gst.exception.GstConversionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Service to combine GSTR-1 payloads from multiple e-commerce platforms (Meesho and Amazon)
 * into a single unified GSTR-1 return matching the GST Portal schema.
 */
@Service
public class GstCombineService {

    private static final Logger log = LoggerFactory.getLogger(GstCombineService.class);

    private final ObjectMapper objectMapper;

    public GstCombineService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    public GstCombineService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * Combines Meesho and Amazon GSTR-1 payloads into a single unified Gstr1Payload.
     * If only one is provided, returns that payload.
     */
    public Gstr1Payload combine(Gstr1Payload meeshoPayload, Gstr1Payload amazonPayload) {
        if (meeshoPayload == null && amazonPayload == null) {
            return null;
        }
        if (meeshoPayload == null) {
            return amazonPayload;
        }
        if (amazonPayload == null) {
            return meeshoPayload;
        }

        String gstin = meeshoPayload.getGstin() != null ? meeshoPayload.getGstin() : amazonPayload.getGstin();
        String fp = meeshoPayload.getFp() != null ? meeshoPayload.getFp() : amazonPayload.getFp();

        Gstr1Payload combined = new Gstr1Payload(gstin, fp);
        combined.setVersion("GST3.2.3");
        combined.setHash("hash");

        // 1. Combine Table 7: B2CS items by POS and Rate
        Map<String, CombinedB2csBucket> b2csMap = new LinkedHashMap<>();

        addB2csItems(b2csMap, meeshoPayload.getB2cs());
        addB2csItems(b2csMap, amazonPayload.getB2cs());

        List<B2csItem> combinedB2cs = new ArrayList<>();
        List<CombinedB2csBucket> sortedBuckets = new ArrayList<>(b2csMap.values());
        sortedBuckets.sort(Comparator.comparing(b -> b.pos));

        for (CombinedB2csBucket bucket : sortedBuckets) {
            double netTxval = round(bucket.txval, 2);
            if (netTxval == 0.0) {
                continue;
            }

            B2csItem item = new B2csItem();
            item.setPos(bucket.pos);
            item.setRt(bucket.rate);
            item.setTyp(bucket.typ);
            item.setTxval(netTxval);
            item.setCsamt(0);

            if ("INTRA".equalsIgnoreCase(bucket.splyTy)) {
                item.setSply_ty("INTRA");
                double calculatedHalf = round(netTxval * (bucket.rate.doubleValue() / 200.0), 2);
                item.setCamt(Math.max(round(bucket.camt, 2), calculatedHalf));
                item.setSamt(Math.max(round(bucket.samt, 2), calculatedHalf));
                item.setIamt(null);
            } else {
                item.setSply_ty("INTER");
                double calculatedTax = round(netTxval * (bucket.rate.doubleValue() / 100.0), 2);
                item.setIamt(Math.max(round(bucket.iamt, 2), calculatedTax));
                item.setCamt(null);
                item.setSamt(null);
            }
            combinedB2cs.add(item);
        }
        combined.setB2cs(combinedB2cs);

        // 2. Combine Table 12: HSN Summary
        // Put Amazon HSN first (if present), then Meesho HSN (matching official GST portal output)
        List<HsnItem> allHsn = new ArrayList<>();
        if (amazonPayload.getHsn() != null && amazonPayload.getHsn().getHsnB2c() != null) {
            allHsn.addAll(amazonPayload.getHsn().getHsnB2c());
        }
        if (meeshoPayload.getHsn() != null && meeshoPayload.getHsn().getHsnB2c() != null) {
            allHsn.addAll(meeshoPayload.getHsn().getHsnB2c());
        }

        if (!allHsn.isEmpty()) {
            // Group by HSN and Rate if identical, otherwise sequential
            Map<String, HsnItem> mergedHsnMap = new LinkedHashMap<>();
            for (HsnItem item : allHsn) {
                String key = item.getHsnSc() + "_" + item.getRt();
                if (!mergedHsnMap.containsKey(key)) {
                    HsnItem copy = new HsnItem();
                    copy.setHsnSc(item.getHsnSc());
                    copy.setUserDesc(item.getUserDesc() != null ? item.getUserDesc() : "0");
                    copy.setUqc(item.getUqc() != null ? item.getUqc() : "PCS");
                    copy.setQty(item.getQty());
                    copy.setRt(item.getRt());
                    copy.setTxval(item.getTxval());
                    copy.setIamt(item.getIamt() != null ? item.getIamt() : 0.0);
                    copy.setSamt(item.getSamt() != null ? item.getSamt() : 0.0);
                    copy.setCamt(item.getCamt() != null ? item.getCamt() : 0.0);
                    copy.setCsamt(item.getCsamt() != null ? item.getCsamt() : 0.0);
                    mergedHsnMap.put(key, copy);
                } else {
                    HsnItem existing = mergedHsnMap.get(key);
                    int q1 = existing.getQty() != null ? existing.getQty().intValue() : 0;
                    int q2 = item.getQty() != null ? item.getQty().intValue() : 0;
                    existing.setQty(q1 + q2);
                    existing.setTxval(round(existing.getTxval() + item.getTxval(), 2));
                    existing.setIamt(round(existing.getIamt() + (item.getIamt() != null ? item.getIamt() : 0.0), 2));
                    existing.setCamt(round(existing.getCamt() + (item.getCamt() != null ? item.getCamt() : 0.0), 2));
                    existing.setSamt(round(existing.getSamt() + (item.getSamt() != null ? item.getSamt() : 0.0), 2));
                }
            }

            List<HsnItem> combinedHsn = new ArrayList<>();
            int hsnSeq = 1;
            for (HsnItem item : mergedHsnMap.values()) {
                item.setNum(hsnSeq++);
                combinedHsn.add(item);
            }
            combined.setHsn(new HsnContainer(combinedHsn));
        }

        // 3. Combine Table 14: E-Commerce Operator (supeco)
        // Put Amazon ECO first, then Meesho ECO (matching official GST portal output order)
        List<SupecoItem> combinedSupeco = new ArrayList<>();
        if (amazonPayload.getSupeco() != null && amazonPayload.getSupeco().getClttx() != null) {
            combinedSupeco.addAll(amazonPayload.getSupeco().getClttx());
        }
        if (meeshoPayload.getSupeco() != null && meeshoPayload.getSupeco().getClttx() != null) {
            combinedSupeco.addAll(meeshoPayload.getSupeco().getClttx());
        }
        if (!combinedSupeco.isEmpty()) {
            combined.setSupeco(new SupecoContainer(combinedSupeco));
        }

        // 4. Combine Table 13: Document summary (doc_issue)
        List<DocItem> allDocs = new ArrayList<>();
        int docSeq = 1;

        if (meeshoPayload.getDocIssue() != null && meeshoPayload.getDocIssue().getDocDet() != null) {
            for (DocDetItem det : meeshoPayload.getDocIssue().getDocDet()) {
                if (det.getDocs() != null) {
                    for (DocItem d : det.getDocs()) {
                        DocItem copy = new DocItem(docSeq++, d.getFrom(), d.getTo(), d.getTotnum(), d.getCancel(), d.getNetIssue());
                        allDocs.add(copy);
                    }
                }
            }
        }

        if (amazonPayload.getDocIssue() != null && amazonPayload.getDocIssue().getDocDet() != null) {
            for (DocDetItem det : amazonPayload.getDocIssue().getDocDet()) {
                if (det.getDocs() != null) {
                    for (DocItem d : det.getDocs()) {
                        DocItem copy = new DocItem(docSeq++, d.getFrom(), d.getTo(), d.getTotnum(), d.getCancel(), d.getNetIssue());
                        allDocs.add(copy);
                    }
                }
            }
        }

        if (!allDocs.isEmpty()) {
            DocDetItem docDet = new DocDetItem(1, "Invoices for outward supply", allDocs);
            combined.setDocIssue(new DocIssueContainer(Collections.singletonList(docDet)));
        }

        log.info("Combined GSTR-1 payload created with {} B2CS items, {} HSN items, {} ECO items",
                combined.getB2cs().size(),
                combined.getHsn() != null ? combined.getHsn().getHsnB2c().size() : 0,
                combined.getSupeco() != null ? combined.getSupeco().getClttx().size() : 0);

        return combined;
    }

    public String serializePayload(Gstr1Payload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new GstConversionException("Failed to serialize Combined GSTR-1 payload to JSON", e);
        }
    }

    private void addB2csItems(Map<String, CombinedB2csBucket> map, List<B2csItem> items) {
        if (items == null) return;
        for (B2csItem item : items) {
            String key = item.getPos() + "_" + item.getRt();
            CombinedB2csBucket bucket = map.computeIfAbsent(key, k -> new CombinedB2csBucket(item.getPos(), item.getRt(), item.getSply_ty(), item.getTyp()));
            bucket.txval += item.getTxval() != null ? item.getTxval() : 0.0;
            if (item.getIamt() != null) {
                bucket.iamt += item.getIamt();
            }
            if (item.getCamt() != null) {
                bucket.camt += item.getCamt();
            }
            if (item.getSamt() != null) {
                bucket.samt += item.getSamt();
            }
        }
    }

    private double round(double val, int decimals) {
        BigDecimal bd = BigDecimal.valueOf(val);
        bd = bd.setScale(decimals, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

    private static class CombinedB2csBucket {
        String pos;
        Number rate;
        String splyTy;
        String typ;
        double txval = 0.0;
        double iamt = 0.0;
        double camt = 0.0;
        double samt = 0.0;

        CombinedB2csBucket(String pos, Number rate, String splyTy, String typ) {
            this.pos = pos;
            this.rate = rate;
            this.splyTy = splyTy != null ? splyTy : "INTER";
            this.typ = typ != null ? typ : "OE";
        }
    }
}
