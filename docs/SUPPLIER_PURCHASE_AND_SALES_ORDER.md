# Tài Liệu Kỹ Thuật: Module Quản Lý Cung Ứng & Bán Hàng Nông Nghiệp

> **Tác giả:** Đội ngũ Backend API Senior  
> **Phiên bản:** v1.0.0  
> **Căn cứ:** Kiến trúc lõi Farm Management System N02 (Spring Boot 4, PostgreSQL, Stateless JWT, JdbcTemplate, RBAC)

---

## 1. Giới thiệu tổng quan

Hệ thống bổ sung 2 module nghiệp vụ cốt lõi đáp ứng trọn vẹn chuỗi giá trị nông nghiệp khép kín:
1. **Module 1: Supplier & Purchase Management (Quản lý Nhà cung cấp & Mua vật tư phục vụ sản xuất)**
   - Quản lý danh mục nhà cung cấp vật tư (hạt giống, phân bón, thuốc BVTV, trang thiết bị).
   - Lập và theo dõi vòng đời đơn mua hàng (Purchase Order - PO) với chi tiết vật tư, số lượng và đơn giá.
   - **Tự động cập nhật số lượng tồn kho (`warehouse_inventory`)** ngay khi nhận hàng vào kho lưu trữ được chỉ định.
   - Ngăn chặn nhập kho trùng lặp hoặc vi phạm dữ liệu kho/vật tư ngừng hoạt động.

2. **Module 2: Sales & Order Management (Quản lý Khách hàng & Bán nông sản sau thu hoạch)**
   - Quản lý hồ sơ đối tác/khách hàng (thương lái, chuỗi bán buôn, siêu thị, khách lẻ).
   - Lập và quản lý đơn bán nông sản (Sales Order - SO) với danh mục sản phẩm sau thu hoạch (`harvested_product`).
   - **Kiểm soát khả dụng và tự động cập nhật tồn kho nông sản**:
     - Kiểm tra tồn kho thời gian thực với khóa dòng (`SELECT ... FOR UPDATE`).
     - Tự động trừ số lượng khả dụng khi đơn hàng được xác nhận (`CONFIRMED`).
     - Tự động hoàn lại số lượng nếu đơn hàng bị hủy (`CANCELLED`).
     - Chống bán khống (overselling) và xung đột đồng thời (race condition).

---

## 2. Thiết kế Cơ sở dữ liệu (Database Schema)

### 2.1. File migration: `database/12_Supplier_Purchase_Management.sql`
- **`public.supplier`**:
  - `supplier_id SERIAL PRIMARY KEY`
  - `supplier_name VARCHAR(150) NOT NULL UNIQUE (lower(btrim))`
  - `contact_name VARCHAR(100)`
  - `email VARCHAR(254)`
  - `phone VARCHAR(20)`
  - `address VARCHAR(255)`
  - `tax_code VARCHAR(50)`
  - `notes VARCHAR(1000)`
  - `status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE'))`
  - `created_at, updated_at TIMESTAMPTZ`

- **`public.purchase_order`**:
  - `po_id SERIAL PRIMARY KEY`
  - `po_number VARCHAR(50) NOT NULL UNIQUE` (Mã đơn dạng `PO-YYYYMMDD-XXXX`)
  - `supplier_id INTEGER NOT NULL REFERENCES public.supplier(supplier_id)`
  - `farm_id INTEGER NOT NULL REFERENCES public.farm(farm_id)`
  - `warehouse_id INTEGER NOT NULL REFERENCES public.warehouse(warehouse_id)` (Kho tiếp nhận)
  - `order_date DATE NOT NULL`
  - `expected_delivery_date DATE`
  - `actual_delivery_date DATE`
  - `status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','ORDERED','RECEIVED','CANCELLED'))`
  - `total_amount NUMERIC(15,2) NOT NULL DEFAULT 0`
  - `notes VARCHAR(1000)`
  - `created_by INTEGER REFERENCES public.app_user(user_id)`

- **`public.purchase_order_item`**:
  - `po_item_id SERIAL PRIMARY KEY`
  - `po_id INTEGER NOT NULL REFERENCES public.purchase_order(po_id) ON DELETE CASCADE`
  - `material_id INTEGER NOT NULL REFERENCES public.material(material_id)`
  - `quantity NUMERIC(15,3) NOT NULL CHECK (quantity > 0)`
  - `unit_price NUMERIC(15,2) NOT NULL DEFAULT 0`
  - `total_price NUMERIC(15,2) NOT NULL DEFAULT 0`
  - `received_quantity NUMERIC(15,3) NOT NULL DEFAULT 0`
  - `notes VARCHAR(500)`

### 2.2. File migration: `database/13_Sales_Order_Management.sql`
- **`public.customer`**:
  - `customer_id SERIAL PRIMARY KEY`
  - `customer_name VARCHAR(150) NOT NULL UNIQUE (lower(btrim))`
  - `contact_name VARCHAR(100)`
  - `email VARCHAR(254)`
  - `phone VARCHAR(20)`
  - `address VARCHAR(255)`
  - `customer_type VARCHAR(20) NOT NULL DEFAULT 'INDIVIDUAL' CHECK (customer_type IN ('INDIVIDUAL','WHOLESALER','RETAILER','ENTERPRISE'))`
  - `tax_code VARCHAR(50)`
  - `notes VARCHAR(1000)`
  - `status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE'))`

- **`public.sales_order`**:
  - `so_id SERIAL PRIMARY KEY`
  - `so_number VARCHAR(50) NOT NULL UNIQUE` (Mã đơn dạng `SO-YYYYMMDD-XXXX`)
  - `customer_id INTEGER NOT NULL REFERENCES public.customer(customer_id)`
  - `farm_id INTEGER REFERENCES public.farm(farm_id)` (Trang trại xuất bán)
  - `order_date DATE NOT NULL`
  - `delivery_date DATE`
  - `status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','CONFIRMED','SHIPPED','DELIVERED','CANCELLED'))`
  - `payment_status VARCHAR(20) NOT NULL DEFAULT 'UNPAID' CHECK (payment_status IN ('UNPAID','PARTIALLY_PAID','PAID'))`
  - `total_amount NUMERIC(15,2) NOT NULL DEFAULT 0`
  - `delivery_address VARCHAR(255)`
  - `notes VARCHAR(1000)`
  - `created_by INTEGER REFERENCES public.app_user(user_id)`

- **`public.sales_order_item`**:
  - `so_item_id SERIAL PRIMARY KEY`
  - `so_id INTEGER NOT NULL REFERENCES public.sales_order(so_id) ON DELETE CASCADE`
  - `product_id INTEGER NOT NULL REFERENCES public.harvested_product(product_id)`
  - `quantity NUMERIC(15,2) NOT NULL CHECK (quantity > 0)`
  - `unit_price NUMERIC(15,2) NOT NULL DEFAULT 0`
  - `total_price NUMERIC(15,2) NOT NULL DEFAULT 0`
  - `notes VARCHAR(500)`

---

## 3. Danh mục API & Phân quyền RBAC

Tất cả các API tuân thủ chuẩn RESTful, trả về mã trạng thái HTTP chuẩn mực và mã lỗi theo cấu trúc hệ thống `{ data, requestId }` hoặc `{ requestId, timestamp, path, error: { code, message, details } }`.

### 3.1. Phân quyền (Permissions)

| Quyền | Mô tả |
|---|---|
| `suppliers:read` | Xem danh sách và chi tiết nhà cung cấp |
| `suppliers:create` | Thêm nhà cung cấp mới |
| `suppliers:update` | Chỉnh sửa thông tin nhà cung cấp |
| `suppliers:status` | Kích hoạt hoặc ngừng hoạt động nhà cung cấp |
| `purchases:read` | Xem danh sách và chi tiết đơn mua vật tư |
| `purchases:create` | Lập đơn mua vật tư mới |
| `purchases:update` | Chỉnh sửa đơn mua vật tư (khi ở trạng thái DRAFT) |
| `purchases:receive` | Nhập kho vật tư và tự động cập nhật tồn kho |
| `purchases:cancel` | Hủy đơn mua vật tư |
| `customers:read` | Xem danh sách và chi tiết khách hàng |
| `customers:create` | Thêm khách hàng mới |
| `customers:update` | Chỉnh sửa thông tin khách hàng |
| `customers:status` | Kích hoạt hoặc ngừng hoạt động khách hàng |
| `sales:read` | Xem danh sách và chi tiết đơn bán nông sản |
| `sales:create` | Tạo đơn bán nông sản mới |
| `sales:update` | Chỉnh sửa đơn bán nông sản (khi ở trạng thái PENDING) |
| `sales:status` | Thay đổi trạng thái đơn bán (CONFIRMED, SHIPPED, DELIVERED, CANCELLED) |

---

## 4. Chi tiết các Endpoints

### 4.1. Module Nhà Cung Cấp & Mua Vật Tư

#### `GET /api/v1/suppliers`
- **Query params:** `page` (int, default 0), `size` (int, default 20), `search` (string), `status` (`ACTIVE`/`INACTIVE`).
- **Response 200:**
```json
{
  "data": {
    "items": [
      {
        "id": 1,
        "name": "Công Ty Cổ Phần Phân Bón Bình Điền",
        "contactName": "Trần Văn B",
        "email": "binhdien@mail.com",
        "phone": "0912345678",
        "address": "Long An",
        "taxCode": "0300123456",
        "notes": "Phân bón chất lượng cao",
        "status": "ACTIVE",
        "createdAt": "2026-10-18T10:00:00Z",
        "updatedAt": "2026-10-18T10:00:00Z"
      }
    ],
    "page": 0,
    "size": 20,
    "total": 1
  },
  "requestId": "uuid"
}
```

#### `POST /api/v1/suppliers`
- **Body:**
```json
{
  "name": "Công ty Giống Cây Trồng Miền Nam",
  "contactName": "Nguyễn Văn A",
  "email": "lienhe@ssc.com.vn",
  "phone": "02838291234",
  "address": "Quận 1, TP. Hồ Chí Minh",
  "taxCode": "0301234567",
  "notes": "Nhà cung cấp hạt giống bắp, lúa"
}
```
- **Response 201 Created:** `Location: /api/v1/suppliers/{id}`.

#### `POST /api/v1/purchase-orders`
- **Body:**
```json
{
  "supplierId": 1,
  "farmId": 1,
  "warehouseId": 2,
  "poNumber": "PO-20261018-001",
  "orderDate": "2026-10-18",
  "expectedDeliveryDate": "2026-10-25",
  "notes": "Đơn vật tư vụ Mùa",
  "items": [
    {
      "materialId": 10,
      "quantity": "50.000",
      "unitPrice": "15000.00",
      "notes": "Phân NPK 16-16-8"
    },
    {
      "materialId": 12,
      "quantity": "20.000",
      "unitPrice": "18000.00",
      "notes": "Phân Kali"
    }
  ]
}
```
- **Response 201 Created:** `Location: /api/v1/purchase-orders/{id}`.

#### `POST /api/v1/purchase-orders/{id}/receive`
- **Thực hiện:**
  1. Kiểm tra đơn hàng đang ở trạng thái hợp lệ (`ORDERED` hoặc `DRAFT`).
  2. Xác thực kho và vật tư còn hoạt động.
  3. **Tự động cộng dồn số lượng vật tư vào bảng `warehouse_inventory`**:
     ```sql
     INSERT INTO public.warehouse_inventory (warehouse_id, material_id, quantity, last_updated)
     VALUES (?, ?, ?, CURRENT_TIMESTAMP)
     ON CONFLICT (warehouse_id, material_id)
     DO UPDATE SET quantity = warehouse_inventory.quantity + EXCLUDED.quantity, last_updated = CURRENT_TIMESTAMP
     ```
  4. Cập nhật `received_quantity = quantity` cho từng dòng vật tư.
  5. Cập nhật trạng thái đơn thành `RECEIVED`, ghi nhận `actual_delivery_date = CURRENT_DATE`.
  6. Ghi nhật ký kiểm toán `audit_log` cho cả `PURCHASE_ORDER` và `INVENTORY`.
- **Response 200 OK:** Dữ liệu đơn mua hàng sau khi cập nhật trạng thái `RECEIVED`.

---

### 4.2. Module Khách Hàng & Bán Nông Sản

#### `GET /api/v1/customers`
- **Query params:** `page`, `size`, `search`, `status`, `customerType` (`INDIVIDUAL`, `WHOLESALER`, `RETAILER`, `ENTERPRISE`).

#### `POST /api/v1/customers`
- **Body:**
```json
{
  "name": "Chuỗi Cửa Hàng Nông Sản Sạch GreenFarm",
  "contactName": "Phạm Thu Hương",
  "customerType": "RETAILER",
  "phone": "0987654321",
  "email": "kinhdoanh@greenfarm.vn",
  "address": "Cầu Giấy, Hà Nội",
  "taxCode": "0109876543",
  "notes": "Đối tác phân phối rau củ đạt chuẩn VietGAP"
}
```

#### `POST /api/v1/sales-orders`
- **Body:**
```json
{
  "customerId": 1,
  "farmId": 2,
  "status": "CONFIRMED",
  "deliveryDate": "2026-10-22",
  "deliveryAddress": "Kho trung chuyển GreenFarm, Hà Nội",
  "notes": "Giao hàng buổi sáng",
  "items": [
    {
      "productId": 5,
      "quantity": "40.00",
      "unitPrice": "35000.00",
      "notes": "Xoài Cát Hòa Lộc loại 1"
    }
  ]
}
```
- **Cơ chế cập nhật tồn kho nông sản:**
  - Nếu `status = "CONFIRMED"`: Hệ thống khóa bản ghi nông sản (`SELECT ... FOR UPDATE`), kiểm tra `harvested_product.quantity >= requestedQuantity`. Nếu không đủ, trả lỗi `409 CONFLICT: Sản phẩm 'X' không đủ số lượng tồn kho để bán. Hiện có: A, yêu cầu: B`. Nếu đủ, thực hiện trừ tồn kho ngay lập tức.
  - Nếu `status = "PENDING"`: Tạo đơn mà chưa trừ tồn kho ngay.

#### `PUT /api/v1/sales-orders/{id}/status`
- **Body:** `{ "status": "CONFIRMED" | "SHIPPED" | "DELIVERED" | "CANCELLED" }`
- **Logic tự động hoàn kho khi HỦY:**
  - Nếu đơn hàng chuyển sang `CANCELLED` từ trạng thái `CONFIRMED` hoặc `SHIPPED`: Hệ thống **tự động hoàn lại toàn bộ số lượng nông sản đã trừ** vào kho `harvested_product`:
    ```sql
    UPDATE public.harvested_product SET quantity = quantity + ?, updated_at = CURRENT_TIMESTAMP WHERE product_id = ?
    ```
  - Ghi nhật ký `audit_log` hành động `RESTORE_STOCK_CANCEL_SO_{id}`.

---

## 5. Hướng dẫn chạy và triển khai

### Chạy migration cơ sở dữ liệu trên PostgreSQL
Chạy bằng tài khoản owner hoặc postgres:
```powershell
& 'C:\Program Files\PostgreSQL\18\bin\psql.exe' -h 127.0.0.1 -U postgres -d farm_management_erd_v1 -W -v ON_ERROR_STOP=1 -f database/12_Supplier_Purchase_Management.sql -f database/13_Sales_Order_Management.sql
```

### Chạy kiểm thử tự động (Unit & Integration Tests)
```powershell
.\mvnw.cmd test "-Dtest=SupplierAndPurchaseApiTests,SalesAndOrderApiTests"
```
Kết quả kiểm thử: **100% Passed (15/15 tests)** bao gồm toàn bộ luồng nghiệp vụ tạo đơn, xác thực dữ liệu, tính toán tiền, kiểm tra tồn kho, khóa đồng thời và cập nhật kho tự động.

### Giao diện người dùng
Giao diện kết nối trực tiếp với backend qua các file:
- **`purchase.html` & `js/purchase.js`**: Quản lý Nhà cung cấp & Đơn mua hàng (PO).
- **`sales.html` & `js/sales.js`**: Quản lý Khách hàng & Đơn bán nông sản (SO).
