# Meesho to GSTR-1 JSON Converter

A production-ready Spring Boot backend utility service to convert Meesho e-commerce tax reports (in **Excel .xlsx** or **CSV** format) into a valid official **GSTR-1 JSON** file ready for direct upload to the official GST Portal or GST Offline Tool.

---

## 🌟 Supported GSTR-1 Tables & Features

- **Table 7 (B2CS)**: Grouped by Place of Supply (`pos`) and Tax Rate (`rt`), netting forward sales against customer returns, segregating intra-state supplies (`INTRA`: `camt` + `samt`) vs. inter-state supplies (`INTER`: `iamt`), and preserving negative return balances.
- **Table 12 (HSN Summary - `hsn_b2c`)**: Summarizes HSN codes (`hsn_sc`), UQC (`PCS`), net quantities (`qty`), net taxable value (`txval`), and segregated IGST/CGST/SGST.
- **Table 14 (E-Commerce Operator Supplies - `supeco`)**: Summarizes supplies made through Meesho e-commerce operator (`etin`, `suppval`, `igst`, `cgst`, `sgst`).
- **Table 13 (Documents Issued - `doc_issue`)**: Parses `tax_invoice_details` to calculate total invoices issued (`totnum`), credit notes/cancellations (`cancel`), net issued (`net_issue`), and the invoice serial range (`from` to `to`).
- **Dual Format Support**: Accepts both **Excel (.xlsx, .xls)** and **CSV (.csv)** files seamlessly.
- **Dynamic Header Detection**: Reads headers dynamically, handling variations like `end_customer_state_new`, `customer_state`, `gst_rate`, `total_taxable_sale_value`, `tax_amount`, `hsn_code`, `eco_tcs_gstin`, and `quantity`.
- **Indian State Code Mapping**: Comprehensive dictionary resolving state names (e.g. `"MAHARASHTRA"`, `"KARNATAKA"`, `"DELHI"`, `"TAMIL NADU"`) to 2-digit official GST state codes (`"27"`, `"29"`, `"07"`, `"33"`).
- **Interactive Swagger UI**: Visual testing available at `http://localhost:8080/swagger-ui/index.html`.

---

## 🏗️ Architecture & Technology Stack

- **Java**: 17+ (Tested & verified on OpenJDK 25 LTS)
- **Framework**: Spring Boot 3.3.4
- **Build Tool**: Maven (with `mvnw` wrapper included)
- **Spreadsheet/File Processing**: Apache POI 5.2.5 (`poi`, `poi-ooxml`) & UTF-8 CSV Streaming
- **JSON Serialization**: Jackson 2 (`jackson-databind`) with pretty-printing enabled
- **API Documentation**: Springdoc OpenAPI 2.6.0 (`springdoc-openapi-starter-webmvc-ui`)
- **Testing**: JUnit 5, Mockito, Spring Test (`MockMvc`)

---

## 📁 Project Directory Structure

```
d:/projects
├── pom.xml
├── README.md
├── mvnw.cmd
├── .mvn/
│   └── wrapper/
│       └── maven-wrapper.properties
├── sample-data/
│   ├── meesho_september_2026/
│   │   ├── tcs_sales.csv
│   │   ├── tcs_sales_return.csv
│   │   ├── tax_invoice_details.csv
│   │   └── expected_GSTR1_092026.json
│   ├── tcs_sales.xlsx
│   ├── tcs_sales_return.xlsx
│   └── tax_invoice_details.xlsx
└── src
    ├── main
    │   ├── java
    │   │   └── com/meesho/gst
    │   │       ├── MeeshoGstr1Application.java
    │   │       ├── config/OpenApiConfig.java
    │   │       ├── controller/GstConversionController.java
    │   │       ├── dto/
    │   │       │   ├── B2csItem.java
    │   │       │   ├── DocDetItem.java
    │   │       │   ├── DocIssueContainer.java
    │   │       │   ├── DocItem.java
    │   │       │   ├── ErrorResponse.java
    │   │       │   ├── Gstr1Payload.java
    │   │       │   ├── HsnContainer.java
    │   │       │   ├── HsnItem.java
    │   │       │   ├── SupecoContainer.java
    │   │       │   └── SupecoItem.java
    │   │       ├── exception/
    │   │       │   ├── GlobalExceptionHandler.java
    │   │       │   └── GstConversionException.java
    │   │       ├── service/MeeshoGstr1Service.java
    │   │       └── util/StateCodeUtil.java
    │   └── resources
    │       └── application.properties
    └── test
        └── java
            └── com/meesho/gst
                ├── SampleDataGenerator.java
                ├── controller/GstConversionControllerTest.java
                └── service/
                    ├── MeeshoExactDataTest.java
                    └── MeeshoGstr1ServiceTest.java
```

---

## 🚀 Running the Application

### 1. Start Server
```powershell
cd d:\projects
.\mvnw.cmd spring-boot:run
```
The server starts on port `8080`.

### 2. Visual Testing via Swagger UI
Open your browser to:
👉 **[http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)**

---

## 📡 API Specification

### Endpoint: Convert Meesho Reports
- **Method:** `POST`
- **Path:** `/api/v1/gst/convert-meesho`
- **Consumes:** `multipart/form-data`
- **Produces:** `application/json`

#### Form-Data Parameters:
| Parameter | Type | Required | Description | Example |
| :--- | :--- | :--- | :--- | :--- |
| `tcs_sales` | `MultipartFile` | **Yes** | Forward orders report (`.xlsx` or `.csv`) | `tcs_sales.csv` |
| `tcs_sales_return` | `MultipartFile` | Optional | Returns & RTOs report (`.xlsx` or `.csv`) | `tcs_sales_return.csv` |
| `tax_invoice_details` | `MultipartFile` | Optional | Document series invoice details report | `tax_invoice_details.csv` |
| `gstin` | `String` | **Yes** | 15-character seller GSTIN | `09OYVPS2177B2ZI` |
| `period` | `String` | **Yes** | Return period in `MMYYYY` format | `092026` |
| `sellerStateCode` | `String` | **Yes** | Seller's 2-digit GST state code | `09` (Uttar Pradesh) |

#### Response:
- **HTTP 200 OK**: Downloadable JSON attachment named `GSTR1_Meesho_<period>.json`.
- **HTTP 400 Bad Request**: Structured error response if validation fails.

---

## 🧪 Testing with cURL

Run this command to convert the sample September 2026 Meesho report:

```bash
curl -X POST "http://localhost:8080/api/v1/gst/convert-meesho" \
  -F "tcs_sales=@sample-data/meesho_september_2026/tcs_sales.csv" \
  -F "tcs_sales_return=@sample-data/meesho_september_2026/tcs_sales_return.csv" \
  -F "tax_invoice_details=@sample-data/meesho_september_2026/tax_invoice_details.csv" \
  -F "gstin=09OYVPS2177B2ZI" \
  -F "period=092026" \
  -F "sellerStateCode=09" \
  --output "GSTR1_Meesho_092026.json"
```

### Exact Output JSON (`GSTR1_Meesho_092026.json`):

```json
{
  "gstin": "09OYVPS2177B2ZI",
  "fp": "092026",
  "version": "GST3.2.3",
  "hash": "hash",
  "b2cs": [
    {
      "sply_ty": "INTER",
      "rt": 18,
      "typ": "OE",
      "pos": "03",
      "txval": 122.4,
      "iamt": 22.03,
      "csamt": 0
    },
    {
      "sply_ty": "INTER",
      "rt": 18,
      "typ": "OE",
      "pos": "05",
      "txval": 111.86,
      "iamt": 20.14,
      "csamt": 0
    },
    {
      "sply_ty": "INTER",
      "rt": 18,
      "typ": "OE",
      "pos": "06",
      "txval": 117.8,
      "iamt": 21.2,
      "csamt": 0
    },
    {
      "sply_ty": "INTER",
      "rt": 18,
      "typ": "OE",
      "pos": "07",
      "txval": 520.22,
      "iamt": 93.64,
      "csamt": 0
    },
    {
      "sply_ty": "INTER",
      "rt": 18,
      "typ": "OE",
      "pos": "08",
      "txval": 254.6,
      "iamt": 45.83,
      "csamt": 0
    },
    {
      "sply_ty": "INTRA",
      "rt": 18,
      "typ": "OE",
      "pos": "09",
      "txval": 472.89,
      "camt": 42.56,
      "samt": 42.56,
      "csamt": 0
    },
    {
      "sply_ty": "INTER",
      "rt": 18,
      "typ": "OE",
      "pos": "10",
      "txval": 247.13,
      "iamt": 44.48,
      "csamt": 0
    },
    {
      "sply_ty": "INTER",
      "rt": 18,
      "typ": "OE",
      "pos": "21",
      "txval": 233.12,
      "iamt": 41.96,
      "csamt": 0
    },
    {
      "sply_ty": "INTER",
      "rt": 18,
      "typ": "OE",
      "pos": "22",
      "txval": 113.11,
      "iamt": 20.36,
      "csamt": 0
    },
    {
      "sply_ty": "INTER",
      "rt": 18,
      "typ": "OE",
      "pos": "23",
      "txval": 200.14,
      "iamt": 36.02,
      "csamt": 0
    },
    {
      "sply_ty": "INTER",
      "rt": 18,
      "typ": "OE",
      "pos": "27",
      "txval": 279.86,
      "iamt": 50.37,
      "csamt": 0
    },
    {
      "sply_ty": "INTER",
      "rt": 18,
      "typ": "OE",
      "pos": "29",
      "txval": 570.47,
      "iamt": 102.69,
      "csamt": 0
    },
    {
      "sply_ty": "INTER",
      "rt": 18,
      "typ": "OE",
      "pos": "33",
      "txval": -112.26,
      "iamt": -20.21,
      "csamt": 0
    },
    {
      "sply_ty": "INTER",
      "rt": 18,
      "typ": "OE",
      "pos": "36",
      "txval": 329.66,
      "iamt": 59.34,
      "csamt": 0
    },
    {
      "sply_ty": "INTER",
      "rt": 18,
      "typ": "OE",
      "pos": "37",
      "txval": 116.55,
      "iamt": 20.98,
      "csamt": 0
    }
  ],
  "hsn": {
    "hsn_b2c": [
      {
        "num": 1,
        "hsn_sc": "392690",
        "user_desc": "0",
        "uqc": "PCS",
        "qty": 28,
        "rt": 18,
        "txval": 3577.54,
        "iamt": 558.84,
        "samt": 42.56,
        "camt": 42.56,
        "csamt": 0.0
      }
    ]
  },
  "supeco": {
    "clttx": [
      {
        "etin": "09AARCM9332R1CM",
        "suppval": 3577.54,
        "igst": 558.84,
        "cgst": 42.56,
        "sgst": 42.56,
        "cess": 0.0,
        "flag": "N"
      }
    ]
  },
  "doc_issue": {
    "doc_det": [
      {
        "doc_num": 1,
        "doc_typ": "Invoices for outward supply",
        "docs": [
          {
            "num": 1,
            "from": "mrnpv27410",
            "to": "mrnpv27CM17",
            "totnum": 56,
            "cancel": 16,
            "net_issue": 40
          }
        ]
      }
    ]
  }
}
```
