# ỨNG DỤNG QUÉT CỔNG & ƯỚC TÍNH KHOẢNG CÁCH WI-FI (JAVA)
> **Môn học:** Lập trình mạng (Network Programming - LTM)  
> **Ngôn ngữ:** Java (Tương thích Java 11 / 17 / 21 / 26)  
> **Kiến trúc:** Java SE Core + Multi-threading + Modern Swing GUI + CLI  
> **Cơ chế bảo vệ:** **Chỉ quét trong mạng Wi-Fi (Strict Wi-Fi Scoped)**

---

## 1. Giới thiệu tổng quan
Ứng dụng được thiết kế chuyên biệt cho việc kiểm thử an ninh mạng nội bộ và đo lường khoảng cách vô tuyến:
1. **Chỉ quét Wi-Fi (Strict Wi-Fi Isolation):** Ứng dụng tự động phát hiện card mạng Wi-Fi đang kết nối, khóa dải quét trong Subnet Wi-Fi hiện tại (ví dụ: `192.168.1.0/24`). Mọi thao tác quét ra ngoài IP Internet hoặc mạng dây khác đều bị hệ thống chặn và thông báo rõ ràng.
2. **Dò tìm thiết bị trong mạng Wi-Fi (LAN Discovery):** Tự động phát hiện các Client, Router/AP và Host hiện tại bằng phương thức ARP sweep + ICMP + TCP probe đa luồng.
3. **Quét cổng dịch vụ (Port Scanner đa luồng):**
   - Hỗ trợ các preset: Top 30 cổng thông dụng, Cổng Web (80, 443...), Cổng Database, dải 1 - 1024, hoặc tùy chỉnh bất kỳ.
   - Đo thời gian bắt tay TCP Socket (RTT) chính xác tới cấp độ microsecond.
   - Tự động trích xuất Service Banner (HTTP Server header, SSH version, FTP welcome banner...).
4. **Ước tính khoảng cách từ Client đến Scanner:**
   - **Trường hợp Client là Router / Access Point (Gateway):** Áp dụng mô hình toán học **Log-Distance Path Loss Model** dựa trên chỉ số cường độ tín hiệu **RSSI (dBm)** và phần trăm tín hiệu thực tế đọc từ card Wi-Fi.
   - **Trường hợp Client là thiết bị cùng mạng Wi-Fi (Peer Client):** Áp dụng mô hình kết hợp **Độ trễ khứ hồi Round-Trip Time (RTT)** + **Độ trễ hàng đợi CSMA/CA** + **Khoảng cách truyền dẫn gián tiếp qua AP**.
5. **Giao diện hiện đại (Modern Dark Cyber Theme):**
   - **Bản đồ Radar quét 360°:** Vẽ động các vòng tròn cự ly (2m, 5m, 10m, 15m, 20m) với vệt quét xoay thời gian thực.
   - **Thước đo khoảng cách (Distance Gauge):** Phân loại cự ly trực quan kèm chỉ số tin cậy.
   - **Thanh trượt môi trường tương tác:** Cho phép tinh chỉnh hệ số suy hao $n$ ($1.8 \le n \le 4.0$) để quan sát khoảng cách thay đổi theo các điều kiện phòng thực tế.
   - **Xuất báo cáo:** Hỗ trợ xuất dữ liệu ra file TXT, CSV và JSON.

---

## 2. Cơ sở lý thuyết & Thuật toán đo khoảng cách

### A. Đối với Access Point (Router Wi-Fi)
Áp dụng **Mô hình suy hao sóng theo khoảng cách logarit (Log-Distance Path Loss Model)**:

$$RSSI(d) = A - 10 \cdot n \cdot \log_{10}(d)$$

Biến đổi tìm khoảng cách $d$ (mét):

$$d = 10^{\frac{A - RSSI}{10 \cdot n}}$$

Trong đó:
- $RSSI$: Cường độ tín hiệu nhận được đo bằng dBm (lấy trực tiếp từ Windows WLAN API qua lệnh `netsh wlan show interfaces`).
- $A$: Công suất tín hiệu tham chiếu tại khoảng cách chuẩn $d_0 = 1$ mét (khoảng $-42 \text{ dBm}$ cho băng tần 5GHz, $-38 \text{ dBm}$ cho 2.4GHz).
- $n$: Hệ số suy hao môi trường (Path Loss Exponent):
  - $n = 2.0$: Không gian tự do (Free space).
  - $n = 2.4 - 2.8$: Môi trường trong nhà thông thoáng (Indoor LOS).
  - $n = 3.0 - 3.8$: Môi trường trong nhà có tường bê tông, vách ngăn (Indoor NLOS).

---

### B. Đối với thiết bị Client kết nối cùng mạng Wi-Fi
Trong mạng Wi-Fi chuẩn 802.11 Infrastructure mode, các Client không giao tiếp trực tiếp với nhau mà truyền gói tin thông qua Access Point:
$$\text{Scanner} \iff \text{Access Point (Router)} \iff \text{Client}$$

Khoảng cách ước tính giữa Scanner và Client được tính toán dựa trên tổng hợp:
1. **Khoảng cách Scanner $\leftrightarrow$ AP ($d_{AP}$):** Tính chính xác từ RSSI của card Wi-Fi máy Scanner.
2. **Đo độ trễ khứ hồi RTT ($t_{RTT}$):** Thực hiện đo thời gian bắt tay TCP 3-way handshake hoặc ICMP Echo nhiều mẫu.
3. **Độ trễ truyền dẫn vượt mức ($\Delta t$):** 
   $$\Delta t = \max(0, t_{RTT} - t_{stack})$$
   (với $t_{stack} \approx 0.8\text{ms}$ là độ trễ xử lý gói tin nội tại của hệ điều hành).
4. **Ước tính cự ly tương đối giữa Client và AP ($d_{Client-AP}$):**
   $$d_{Client-AP} = d_0 + k \cdot (\Delta t)^{0.75}$$
5. **Khoảng cách vật lý ước lượng giữa Scanner và Client:**
   $$d_{est} = \sqrt{d_{AP}^2 + d_{Client-AP}^2}$$

---

## 3. Cấu trúc thư mục dự án

```
c:\DA WEB\LTM\
├── src\
│   └── com\
│       └── wifiscan\
│           ├── Main.java                      # Điểm khởi chạy (GUI & CLI)
│           ├── model\
│           │   ├── WifiInfo.java              # Dữ liệu Card Wi-Fi, SSID, RSSI, Subnet
│           │   ├── NetworkDevice.java         # Dữ liệu thiết bị phát hiện trong Wi-Fi
│           │   ├── PortResult.java            # Kết quả quét cổng (State, Latency, Banner)
│           │   ├── PortState.java             # Enum: OPEN, CLOSED, FILTERED
│           │   └── DistanceEstimate.java      # Số liệu tính toán và giải thích khoảng cách
│           ├── service\
│           │   ├── WifiDetector.java          # Nhận diện Wi-Fi & Chặn quét ngoài Wi-Fi
│           │   ├── LanScanner.java            # Quét đa luồng các thiết bị trong Wi-Fi Subnet
│           │   ├── PortScanner.java           # Quét cổng TCP đa luồng & Lấy Banner
│           │   ├── DistanceCalculator.java    # Xử lý tính toán công thức Path Loss & RTT
│           │   └── PortDictionary.java        # Danh mục cổng thông dụng và tên dịch vụ
│           └── ui\
│               ├── Theme.java                 # Bảng màu Modern Dark Cyber
│               ├── MainWindow.java            # Cửa sổ chính với 4 Tabs chức năng
│               └── components\
│                   ├── RadarPanel.java        # Radar xoay 360 độ hiển thị vị trí thiết bị
│                   ├── DistanceCard.java       # Thẻ hiển thị khoảng cách & thanh trượt n
│                   ├── WifiBadge.java          # Thanh trạng thái Wi-Fi trên Header
│                   ├── ModernButton.java      # Nút bấm phong cách phẳng bo góc
│                   └── ModernTable.java       # Bảng hiển thị dữ liệu với Badge trạng thái
├── bin\                                       # Chứa file bytecode .class sau khi biên dịch
├── build.bat                                  # Script biên dịch tự động 1-click
├── run.bat                                    # Script chạy giao diện đồ họa GUI
├── run-cli.bat                                # Script chạy giao diện dòng lệnh CLI
└── README.md                                  # Tài liệu hướng dẫn chi tiết
```

---

## 4. Hướng dẫn cài đặt và sử dụng

### Cách 1: Chạy bằng file Batch (Khuyên dùng trên Windows)
- **Biên dịch mã nguồn:** Nháy đúp vào file `build.bat`.
- **Khởi chạy giao diện đồ họa GUI:** Nháy đúp vào file `run.bat`.
- **Khởi chạy chế độ dòng lệnh CLI:** Nháy đúp vào file `run-cli.bat`.

### Cách 2: Chạy bằng dòng lệnh Terminal (PowerShell / CMD)
```powershell
# Di chuyển vào thư mục dự án
cd "c:\DA WEB\LTM"

# 1. Biên dịch toàn bộ mã nguồn
javac -d bin -encoding UTF-8 (Get-ChildItem -Recurse -Filter *.java src | ForEach-Object { $_.FullName })

# 2. Khởi chạy Giao diện đồ họa (GUI)
java -cp bin com.wifiscan.Main

# 3. Hoặc chạy chế độ Dòng lệnh (CLI) quét Gateway mặc định
java -cp bin com.wifiscan.Main --cli

# 4. Quét CLI tới một IP client cụ thể trong mạng Wi-Fi
java -cp bin com.wifiscan.Main --cli 192.168.1.22
```

---

## 5. Thử nghiệm thực tế đã kiểm chứng
- **Card Wi-Fi nhận diện:** Intel(R) Wireless-AC 9260 160MHz
- **Mạng Wi-Fi kết nối:** SSID `PHONG`, Băng tần `5 GHz`, Kênh `44`
- **Tín hiệu RSSI:** `-44 dBm` (Tín hiệu 91%)
- **IP Scanner:** `192.168.1.69`, Gateway: `192.168.1.1`
- **Kết quả đo khoảng cách tới Router Wi-Fi (Gateway):** `~ 1.19 mét` (Cực gần, cùng phòng).
- **Kết quả quét cổng Gateway (192.168.1.1):**
  - Cổng 53/TCP (DNS): MỞ (3.36 ms)
  - Cổng 80/TCP (HTTP Web Management): MỞ (3.28 ms)
  - Cổng 443/TCP (HTTPS): MỞ (2.71 ms)
  - Thời gian quét 29 cổng: **0.81 giây**!
- **Kiểm thử cơ chế bảo vệ (Chỉ scan Wi-Fi):** Khi thử quét IP ngoài `8.8.8.8`, hệ thống lập tức kích hoạt cơ chế phòng vệ và từ chối quét.
