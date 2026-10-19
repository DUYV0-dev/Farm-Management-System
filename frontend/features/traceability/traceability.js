/**
 * AgriManage - Product Traceability & Provenance Module JS
 * Handles Crop Season info, Farming Activity History, Fertilizer & Pesticide usage (PHI compliance),
 * Harvest records, QR Code generation, and Consumer mobile verification.
 */

document.addEventListener('DOMContentLoaded', () => {
    // 1. Initial Seed Batches with Deep Traceability Data
    const DEFAULT_BATCHES = [
        {
            id: 'LOT-2026-ST25-01',
            productName: 'Gạo Thơm Thượng Hạng ST25',
            variety: 'ST25 Thuần Chủng F1 Sóc Trăng',
            season: 'Mùa Đông Xuân 2026',
            cropDuration: '15/11/2025 ➔ 20/03/2026 (105 ngày)',
            farmLocation: 'Cánh đồng Lúa Sector 1, H. Tháp Mười, Tỉnh Đồng Tháp',
            gpsCoordinates: '10°34\'18.2"N 105°47\'22.6"E',
            pucCode: 'VN-DTP-0832-PUC',
            certification: 'VietGAP',
            certNumber: 'VietGAP-TT-26-01824',
            manager: 'Trần Văn Lượng (Quản lý vườn)',
            inspector: 'Lê Thị Mai (Kỹ thuật viên KCS)',
            productImg: 'https://images.unsplash.com/photo-1586201375761-83865001e31c?auto=format&fit=crop&w=600&q=80',
            productStory: 'Gạo ST25 được canh tác theo mô hình Lúa - Tôm tự nhiên bền vững, kiểm soát nguồn nước phù sa sông Tiền, không sử dụng thuốc trừ sâu hóa học độc hại, cơm dẻo mềm và thơm ngát hương lá dứa tự nhiên.',
            harvestActualTime: '20/03/2026 - 08:30',
            harvestActualYield: '15,500 kg (15.5 tấn)',
            harvestGradeRatio: 'Loại 1: 98% | Loại 2: 2%',
            packaging: 'Đóng túi hút chân không tráng bạc 5kg tiêu chuẩn xuất khẩu, dán tem QR chống hàng giả độc bản.',
            storageCondition: 'Bảo quản nơi khô ráo, nhiệt độ phòng 20-25°C, độ ẩm < 14%. HSD: 12 tháng.',
            expiryDate: '20/03/2027',
            journey: [
                { step: 1, title: 'Làm đất & Khử trùng', date: '10/11/2025', desc: 'Cày sâu 25cm, bón vôi sinh học cân bằng pH đạt 6.5.', status: 'completed' },
                { step: 2, title: 'Gieo cấy mạ ST25', date: '15/11/2025', desc: 'Xuống giống mạ khỏe, tỷ lệ nảy mầm 98.5%.', status: 'completed' },
                { step: 3, title: 'Chăm sóc & Bón thúc', date: '05/12/2025', desc: 'Bón phân hữu cơ vi sinh, tưới ngập khô xen kẽ.', status: 'completed' },
                { step: 4, title: 'Kiểm soát sinh học (PHI)', date: '18/12/2025', desc: 'Phun chế phẩm vi sinh BT phòng ngừa sâu bệnh an toàn.', status: 'completed' },
                { step: 5, title: 'Thu hoạch & Đóng gói', date: '20/03/2026', desc: 'Gặt đập liên hợp, sấy lạnh đóng túi chân không dán tem.', status: 'completed' }
            ],
            activities: [
                { date: '10/11/2025', category: 'Làm đất', title: 'Cày ải phơi đất & Rải vôi bột sinh học khử khuẩn', operator: 'Trần Văn Lượng', status: 'Hoàn thành (100%)', notes: 'Độ ẩm đất đạt 68%, pH 6.5, diện tích 2.5 ha' },
                { date: '15/11/2025', category: 'Gieo trồng', title: 'Gieo sạ hàng giống lúa thuần chủng ST25 dòng F1', operator: 'Nguyễn Văn Hùng', status: 'Hoàn thành (100%)', notes: 'Mật độ 100kg/ha, giống được ngâm ủ mầm đạt 98.5%' },
                { date: '05/12/2025', category: 'Bón phân', title: 'Bón thúc đợt 1 phân NPK kết hợp chế phẩm Humic', operator: 'Phạm Thanh Sơn', status: 'Hoàn thành (100%)', notes: 'Kích thích đẻ nhánh hữu hiệu, rễ bám sâu' },
                { date: '18/12/2025', category: 'Phun thuốc', title: 'Phun chế phẩm sinh học BT bảo vệ thiên địch', operator: 'Lê Thị Mai', status: 'Hoàn thành (100%)', notes: 'Phòng trừ sâu cuốn lá mầm non, an toàn sinh học' },
                { date: '10/01/2026', category: 'Bón phân', title: 'Bón đón đòng đợt 2 vi lượng Canxi - Bo hữu cơ', operator: 'Nguyễn Văn Hùng', status: 'Hoàn thành (100%)', notes: 'Hạt vào chắc, bông to, chống nghẹn đòng' },
                { date: '10/03/2026', category: 'Tưới nước', title: 'Rút cạn nước ruộng chuẩn bị gặt thu hoạch', operator: 'Trần Văn Lượng', status: 'Hoàn thành (100%)', notes: 'Rút nước trước 10 ngày để hạt lúa chín khô ráo, vàng sáng' },
                { date: '20/03/2026', category: 'Thu hoạch', title: 'Gặt đập cơ giới liên hợp & Nghiệm thu xuất xưởng', operator: 'Nguyễn Văn Hùng', status: 'Hoàn thành (100%)', notes: 'Thu hoạch đạt 15,500 kg, tỷ lệ hạt chắc đạt 98%' }
            ],
            fertilizers: [
                { date: '15/11/2025', name: 'Phân hữu cơ vi sinh Quế Lâm', type: 'Hữu cơ vi sinh', dose: '500 kg/ha', stage: 'Bón lót làm đất', operator: 'Nguyễn Văn Hùng', status: 'Đã hoàn thành' },
                { date: '05/12/2025', name: 'Phân NPK 20-20-15 Hải Phong', type: 'Vô cơ vi lượng', dose: '180 kg/ha', stage: 'Bón thúc đẻ nhánh', operator: 'Phạm Thanh Sơn', status: 'Đã hoàn thành' },
                { date: '10/01/2026', name: 'Phân bón lá hữu cơ Canxi-Bo', type: 'Hữu cơ sinh học', dose: '2.5 lít/ha', stage: 'Bón đón đòng', operator: 'Nguyễn Văn Hùng', status: 'Đã hoàn thành' }
            ],
            pesticides: [
                { date: '18/12/2025', name: 'Chế phẩm vi sinh BT (Bacillus)', active: 'Vi khuẩn tự nhiên BT', target: 'Sâu cuốn lá lúa', dose: '1.5 lít/ha', phiDays: 3, phiSafe: true, safeDate: '21/12/2025', operator: 'Lê Thị Mai', verdict: 'ĐẠT AN TOÀN TUYỆT ĐỐI (Cách ly trước thu hoạch 90 ngày)' },
                { date: '15/01/2026', name: 'Dầu neem sinh học Bio-Neem', active: 'Azadirachtin 1500ppm', target: 'Bọ trĩ & rầy nâu', dose: '1.0 lít/ha', phiDays: 5, phiSafe: true, safeDate: '20/01/2026', operator: 'Lê Thị Mai', verdict: 'ĐẠT AN TOÀN TUYỆT ĐỐI (Cách ly trước thu hoạch 60 ngày)' }
            ]
        },
        {
            id: 'LOT-2026-CHOC-02',
            productName: 'Cà Chua Sô Cô La Isreal F1',
            variety: 'Isreal Chocolate Tomato F1',
            season: 'Mùa Hè Thu 2026',
            cropDuration: '01/06/2026 ➔ 24/09/2026 (115 ngày)',
            farmLocation: 'Nhà màng Greenhouse A2, TP. Đà Lạt, Tỉnh Lâm Đồng',
            gpsCoordinates: '11°56\'24.8"N 108°26\'15.2"E',
            pucCode: 'VN-LDO-0428-PUC',
            certification: 'GlobalGAP',
            certNumber: 'GGN-4059883726189',
            manager: 'Trần Văn Lượng (Quản lý nhà màng)',
            inspector: 'Hồng Thái Vinh (KCS)',
            productImg: 'https://images.unsplash.com/photo-1592924357228-91a4daadcfea?auto=format&fit=crop&w=600&q=80',
            productStory: 'Cà chua sô cô la Isreal trồng trong nhà màng công nghệ cao Israel, kiểm soát dinh dưỡng tưới nhỏ giọt tự động Fertigation, vỏ nâu bóng giàu anthocyanin chống oxy hóa, vị ngọt đậm giòn tan.',
            harvestActualTime: '24/09/2026 - 15:00',
            harvestActualYield: '1,850 kg',
            harvestGradeRatio: 'Loại 1: 96% | Loại 2: 4%',
            packaging: 'Đóng khay bã mía 500g bọc màng thở sinh học, dán tem QR truy xuất GlobalGAP.',
            storageCondition: 'Bảo quản kho mát nhiệt độ 12-15°C, độ ẩm 85%. HSD: 14 ngày.',
            expiryDate: '08/10/2026',
            journey: [
                { step: 1, title: 'Khử trùng giá thể xơ dừa', date: '01/06/2026', desc: 'Xử lý hơi nước nhiệt độ cao tiệt trùng nấm mốc giá thể.', status: 'completed' },
                { step: 2, title: 'Trồng cây con F1 vào bầu', date: '08/06/2026', desc: 'Cây con 20 ngày tuổi đạt 4 lá thật, thân mập khỏe.', status: 'completed' },
                { step: 3, title: 'Tưới nhỏ giọt Fertigation', date: '25/06/2026', desc: 'Điều tiết EC 1.8 mS/cm, pH 6.0 theo cảm biến IoT.', status: 'completed' },
                { step: 4, title: 'Thụ phấn bằng ong chuyên dụng', date: '15/07/2026', desc: 'Sử dụng ong nghệ Bombus thụ phấn tự nhiên.', status: 'completed' },
                { step: 5, title: 'Hái chọn lọc & Đóng khay', date: '24/09/2026', desc: 'Thu hoạch đạt độ chín 85%, đóng khay dán tem xuất khẩu.', status: 'completed' }
            ],
            activities: [
                { date: '01/06/2026', category: 'Làm đất', title: 'Xử lý giá thể mùn xơ dừa và tiệt trùng nhà màng A2', operator: 'Trần Văn Lượng', status: 'Hoàn thành (100%)', notes: 'Giá thể xơ dừa ngâm rửa tanin, EC < 0.5' },
                { date: '08/06/2026', category: 'Gieo trồng', title: 'Trồng cây giống cà chua sô cô la Isreal F1 vào máng', operator: 'Lê Thị Mai', status: 'Hoàn thành (100%)', notes: 'Mật độ 2.8 cây/m2, lắp đặt đầu cắm nhỏ giọt' },
                { date: '10/07/2026', category: 'Bón phân', title: 'Hòa tan công thức dinh dưỡng A-B hệ thống tưới nhỏ giọt', operator: 'Võ Hà Duy', status: 'Hoàn thành (100%)', notes: 'Tự động kiểm soát qua bộ PLC trung tâm' },
                { date: '05/08/2026', category: 'Phun thuốc', title: 'Phun nấm đối kháng Trichoderma phòng thối rễ', operator: 'Lê Thị Mai', status: 'Hoàn thành (100%)', notes: 'Chế phẩm sinh học 100%, thời gian cách ly 0 ngày' },
                { date: '24/09/2026', category: 'Thu hoạch', title: 'Thu hoạch cà chua chín bói đợt 1 và đóng thùng', operator: 'Phạm Thanh Sơn', status: 'Hoàn thành (100%)', notes: 'Sản lượng 1,850kg đạt tiêu chuẩn GlobalGAP xuất khẩu' }
            ],
            fertilizers: [
                { date: '10/06/2026', name: 'Bộ dinh dưỡng thủy canh Haifa Multi-K', type: 'Khoáng hòa tan', dose: '25 kg/1000L', stage: 'Sinh trưởng thân lá', operator: 'Võ Hà Duy', status: 'Đã hoàn thành' },
                { date: '15/07/2026', name: 'Canxi Nitrat & Magie Sunfat Haifa', type: 'Vi lượng hòa tan', dose: '15 kg/1000L', stage: 'Nuôi hoa & Đậu quả', operator: 'Võ Hà Duy', status: 'Đã hoàn thành' }
            ],
            pesticides: [
                { date: '05/08/2026', name: 'Chế phẩm nấm ký sinh Beauveria bassiana', active: 'Nấm trắng ký sinh', target: 'Bọ phấn trắng & bọ trĩ', dose: '1.0 kg/ha', phiDays: 1, phiSafe: true, safeDate: '06/08/2026', operator: 'Lê Thị Mai', verdict: 'ĐẠT AN TOÀN TUYỆT ĐỐI (Cách ly trước thu hoạch 50 ngày)' }
            ]
        },
        {
            id: 'LOT-2026-MELON-03',
            productName: 'Dưa Lưới Huỳnh Long Ruột Cam',
            variety: 'Dưa lưới Huỳnh Long F1 Nhật Bản',
            season: 'Mùa Hè Thu 2026',
            cropDuration: '10/07/2026 ➔ 02/10/2026 (85 ngày)',
            farmLocation: 'Nhà kính Greenhouse C1, Xã Lạc Dương, Tỉnh Lâm Đồng',
            gpsCoordinates: '11°58\'12.4"N 108°27\'44.1"E',
            pucCode: 'VN-LDO-0915-PUC',
            certification: 'VietGAP',
            certNumber: 'VietGAP-DL-26-00492',
            manager: 'Trần Văn Lượng (Quản lý vườn)',
            inspector: 'Lê Thị Mai (KCS)',
            productImg: 'https://images.unsplash.com/photo-1598170845058-32b9d6a5da37?auto=format&fit=crop&w=600&q=80',
            productStory: 'Dưa lưới Huỳnh Long có vân lưới nổi đều đặn, ruột cam giòn ngọt lịm với độ đường brix đạt từ 14.5 - 15.5%, canh tác treo dây tiêu chuẩn Nhật Bản trong nhà kính chống sâu bọ tuyệt đối.',
            harvestActualTime: '02/10/2026 - 07:00',
            harvestActualYield: '4,200 kg',
            harvestGradeRatio: 'Loại 1: 95% (trên 1.6kg) | Loại 2: 5%',
            packaging: 'Bọc lưới mút xốp chống va đập, thùng carton 4 quả/thùng có tem QR code.',
            storageCondition: 'Kho lạnh 10-12°C, độ ẩm 80%. HSD: 20 ngày.',
            expiryDate: '22/10/2026',
            journey: [
                { step: 1, title: 'Ươm mầm hạt giống F1', date: '10/07/2026', desc: 'Ươm khay 84 lỗ, tưới giữ ẩm phòng ươm vi khí hậu.', status: 'completed' },
                { step: 2, title: 'Trồng vào chậu & Treo dây', date: '18/07/2026', desc: 'Cố định thân dây leo cao 2.2m thẳng tắp.', status: 'completed' },
                { step: 3, title: 'Thụ phấn & Tuyển quả', date: '12/08/2026', desc: 'Thụ phấn nách lá 10-12, chỉ để lại 1 quả đẹp nhất/dây.', status: 'completed' },
                { step: 4, title: 'Tạo vân lưới & Giảm nước', date: '05/09/2026', desc: 'Kiểm soát chênh lệch ẩm độ tạo lưới dày, xiết nước tăng độ ngọt brix.', status: 'completed' },
                { step: 5, title: 'Cắt cuống hình chữ T & Đóng thùng', date: '02/10/2026', desc: 'Cắt tỉ mỉ giữ nguyên cuống tươi xanh dán tem truy xuất.', status: 'completed' }
            ],
            activities: [
                { date: '10/07/2026', category: 'Gieo trồng', title: 'Ươm hạt dưa lưới Huỳnh Long trong khay giá thể', operator: 'Lê Thị Mai', status: 'Hoàn thành (100%)', notes: 'Tỷ lệ nảy mầm 99%' },
                { date: '12/08/2026', category: 'Kiểm tra & Bảo trì', title: 'Thụ phấn nhân tạo hoa cái và tuyển chọn quả duy nhất', operator: 'Phạm Thanh Sơn', status: 'Hoàn thành (100%)', notes: 'Tuyển quả cuống to, bầu dục tròn đều' },
                { date: '05/09/2026', category: 'Bón phân', title: 'Bổ sung Kali Sunfat sinh học giúp dưa lên vân lưới đẹp', operator: 'Nguyễn Văn Hùng', status: 'Hoàn thành (100%)', notes: 'Đo độ brix quả đạt 13.5' },
                { date: '02/10/2026', category: 'Thu hoạch', title: 'Thu hoạch dưa lưới cuống chữ T và đóng thùng carton', operator: 'Phạm Thanh Sơn', status: 'Hoàn thành (100%)', notes: 'Độ đường bình quân 15.2 brix, sản lượng 4.2 tấn' }
            ],
            fertilizers: [
                { date: '20/07/2026', name: 'Phân vi sinh rong biển Seamix', type: 'Hữu cơ biển sâu', dose: '10 kg/1000L', stage: 'Phát triển thân nhánh', operator: 'Nguyễn Văn Hùng', status: 'Đã hoàn thành' },
                { date: '05/09/2026', name: 'Phân bón lá hữu cơ Kali Hữu Cơ', type: 'Hữu cơ sinh học', dose: '5 kg/1000L', stage: 'Tạo lưới & Tích lũy đường', operator: 'Nguyễn Văn Hùng', status: 'Đã hoàn thành' }
            ],
            pesticides: [
                { date: '15/08/2026', name: 'Dầu khoáng thảo mộc SK Enspray', active: 'Khoáng tự nhiên tinh chế', target: 'Nhện đỏ & Bọ trĩ', dose: '0.8 lít/ha', phiDays: 2, phiSafe: true, safeDate: '17/08/2026', operator: 'Lê Thị Mai', verdict: 'ĐẠT AN TOÀN TUYỆT ĐỐI (Cách ly trước thu hoạch 48 ngày)' }
            ]
        },
        {
            id: 'LOT-2026-STRAW-04',
            productName: 'Dâu Tây Bạch Tuyết Ngọc Trai Đà Lạt',
            variety: 'Shiroi Houseki Bạch Tuyết F1',
            season: 'Mùa Đông Xuân 2026',
            cropDuration: '01/10/2025 ➔ 14/10/2026 (Thu hoạch quanh năm)',
            farmLocation: 'Khu Thủy canh Tầng cao Greenhouse D, Phường 7, TP. Đà Lạt',
            gpsCoordinates: '11°59\'05.1"N 108°25\'22.0"E',
            pucCode: 'VN-LDO-0188-PUC',
            certification: 'Organic',
            certNumber: 'ORGANIC-VN-26-8891',
            manager: 'Trần Văn Lượng (Quản lý vườn)',
            inspector: 'Lê Thị Mai (KCS)',
            productImg: 'https://images.unsplash.com/photo-1518635017498-87f514b751ba?auto=format&fit=crop&w=600&q=80',
            productStory: 'Giống dâu tây Bạch Tuyết Nhật Bản vỏ trắng tinh khôi, mắt đỏ ngọc bích, thơm ngát mùi dứa chín và vị ngọt thanh quý tộc. Được canh tác giàn thủy canh giá thể xơ dừa cách ly mặt đất 1.2m.',
            harvestActualTime: '14/10/2026 - 06:30',
            harvestActualYield: '650 kg',
            harvestGradeRatio: 'Loại VIP: 90% | Loại 1: 10%',
            packaging: 'Hộp mica định hình từng quả 250g có lót nệm khí, dán tem OCOP 5 Sao.',
            storageCondition: 'Nhiệt độ tủ mát 4-7°C. HSD: 7 ngày.',
            expiryDate: '21/10/2026',
            journey: [
                { step: 1, title: 'Cấy ngó dâu F1 sạch bệnh', date: '01/10/2025', desc: 'Ngó nuôi cấy mô tinh khiết kháng virus.', status: 'completed' },
                { step: 2, title: 'Lên giàn thủy canh tầng cao', date: '15/10/2025', desc: 'Hệ thống giàn A-Frame hứng nắng tối đa.', status: 'completed' },
                { step: 3, title: 'Bón dinh dưỡng vi sinh hữu cơ', date: '10/11/2025', desc: 'Sử dụng đạm cá lên men vi sinh và dịch chuối ủ vi lượng.', status: 'completed' },
                { step: 4, title: 'Bọc màng lưới bảo vệ chùm quả', date: '05/12/2025', desc: 'Quả trắng không chạm giá thể, vỏ mịn không tì vết.', status: 'completed' },
                { step: 5, title: 'Hái thủ công đeo găng tay lụa', date: '14/10/2026', desc: 'Hái sáng sớm khi quả còn ngậm sương, đóng hộp dán tem.', status: 'completed' }
            ],
            activities: [
                { date: '01/10/2025', category: 'Gieo trồng', title: 'Trồng ngó dâu Bạch Tuyết mô sạch vào máng giá thể', operator: 'Lê Thị Mai', status: 'Hoàn thành (100%)', notes: 'Ngó mô sạch rễ phát triển cực mạnh' },
                { date: '10/11/2025', category: 'Bón phân', title: 'Tưới dinh dưỡng đạm cá hồi sinh học và enzym chuối', operator: 'Nguyễn Văn Hùng', status: 'Hoàn thành (100%)', notes: 'Độ ngọt và hương thơm dứa đậm đà' },
                { date: '14/10/2026', category: 'Thu hoạch', title: 'Hái dâu tây Bạch Tuyết tuyển chọn và đóng hộp VIP', operator: 'Lê Thị Mai', status: 'Hoàn thành (100%)', notes: 'Sản lượng 650kg dán tem OCOP 5 sao' }
            ],
            fertilizers: [
                { date: '10/11/2025', name: 'Dịch đạm cá hồi hữu cơ Bio-Fish', type: 'Hữu cơ lên men', dose: '5 lít/1000L', stage: 'Nuôi thân cành & Đậu trái', operator: 'Nguyễn Văn Hùng', status: 'Đã hoàn thành' }
            ],
            pesticides: [
                { date: '01/09/2026', name: 'Tinh dầu quế & Tỏi sinh học hữu cơ', active: 'Cinnamaldehyde tự nhiên', target: 'Xua đuổi côn trùng', dose: '1.0 lít/ha', phiDays: 0, phiSafe: true, safeDate: '01/09/2026', operator: 'Lê Thị Mai', verdict: '100% ORGANIC KHÔNG DÙNG THUỐC BVTV HÓA HỌC' }
            ]
        },
        {
            id: 'LOT-2026-CORN-05',
            productName: 'Ngô Ngọt Đường F1 Hưng Yên',
            variety: 'Bắp ngọt Thái Lan Super Sweet Corn F1',
            season: 'Mùa Thu Đông 2026',
            cropDuration: '01/08/2026 ➔ 15/10/2026 (75 ngày)',
            farmLocation: 'Cánh đồng Lô D3 - Phía Nam, Xã Yên Mỹ, Tỉnh Hưng Yên',
            gpsCoordinates: '20°54\'40.2"N 106°01\'18.5"E',
            pucCode: 'VN-HYN-0341-PUC',
            certification: 'VietGAP',
            certNumber: 'VietGAP-HY-26-00318',
            manager: 'Trần Văn Lượng (Quản lý vườn)',
            inspector: 'Phạm Thanh Sơn (KCS)',
            productImg: 'https://images.unsplash.com/photo-1551754655-cd27e38d2076?auto=format&fit=crop&w=600&q=80',
            productStory: 'Bắp ngọt F1 hạt vàng óng, mọng sữa ngọt tự nhiên, bắp dài trên 20cm, hạt bọc kín tới đầu bắp, canh tác luân canh trên đất bãi phù sa màu mỡ sông Hồng đạt tiêu chuẩn an toàn VietGAP.',
            harvestActualTime: '15/10/2026 - 06:00',
            harvestActualYield: '3,800 kg',
            harvestGradeRatio: 'Loại 1: 94% | Loại 2: 6%',
            packaging: 'Đóng thùng sọt lưới 30 bắp/sọt có dán tem mã QR theo dõi truy xuất.',
            storageCondition: 'Bảo quản nơi thoáng mát, tốt nhất chế biến trong 72h.',
            expiryDate: '25/10/2026',
            journey: [
                { step: 1, title: 'Lên luống & Bón lót', date: '01/08/2026', desc: 'Luống cao 30cm, bón lót phân chuồng ủ hoai mục.', status: 'completed' },
                { step: 2, title: 'Tra hạt giống bắp ngọt F1', date: '05/08/2026', desc: 'Khoảng cách cây 30cm, hàng cách hàng 70cm.', status: 'completed' },
                { step: 3, title: 'Chăm sóc & Vun gốc', date: '25/08/2026', desc: 'Vun gốc chống đổ ngã, bón thúc NPK cân đối.', status: 'completed' },
                { step: 4, title: 'Phun phòng sâu đục bắp sinh học', date: '15/09/2026', desc: 'Phun chế phẩm sinh học thảo mộc cách ly an toàn.', status: 'completed' },
                { step: 5, title: 'Bẻ bắp sáng sớm & Xuất kho', date: '15/10/2026', desc: 'Bẻ bắp khi hạt căng mọng sữa, chuyển ngay về kho mát.', status: 'completed' }
            ],
            activities: [
                { date: '01/08/2026', category: 'Làm đất', title: 'Cày sâu lên luống cao 30cm rãnh thoát nước', operator: 'Trần Văn Lượng', status: 'Hoàn thành (100%)', notes: 'Đất phù sa tơi xốp, thoát nước tốt' },
                { date: '05/08/2026', category: 'Gieo trồng', title: 'Tra hạt bắp ngọt Thái Lan theo hàng thẳng', operator: 'Nguyễn Văn Hùng', status: 'Hoàn thành (100%)', notes: 'Hạt nảy mầm sau 3 ngày đạt 98%' },
                { date: '15/10/2026', category: 'Thu hoạch', title: 'Thu hoạch bẻ bắp ngọt buổi sáng sớm và nghiệm thu', operator: 'Phạm Thanh Sơn', status: 'Hoàn thành (100%)', notes: 'Sản lượng 3,800kg, hạt vàng óng, bắp đều' }
            ],
            fertilizers: [
                { date: '01/08/2026', name: 'Phân hữu cơ hoai mục Sông Hồng', type: 'Hữu cơ truyền thống', dose: '800 kg/ha', stage: 'Bón lót', operator: 'Trần Văn Lượng', status: 'Đã hoàn thành' },
                { date: '25/08/2026', name: 'Phân NPK 16-16-8 Đầu Trâu', type: 'Vô cơ khoáng chất', dose: '200 kg/ha', stage: 'Bón thúc đợt 1', operator: 'Nguyễn Văn Hùng', status: 'Đã hoàn thành' }
            ],
            pesticides: [
                { date: '15/09/2026', name: 'Chế phẩm thảo mộc xua đuổi sâu đục thân', active: 'Chiết xuất ớt cay & tỏi', target: 'Sâu đục bắp', dose: '1.2 lít/ha', phiDays: 3, phiSafe: true, safeDate: '18/09/2026', operator: 'Lê Thị Mai', verdict: 'ĐẠT AN TOÀN TUYỆT ĐỐI (Cách ly trước thu hoạch 27 ngày)' }
            ]
        }
    ];

    // Local Storage Helpers
    function getBatches() {
        const stored = localStorage.getItem('agri_traceability_lots');
        if (stored) {
            try { return JSON.parse(stored); } catch (e) { }
        }
        return DEFAULT_BATCHES;
    }

    function saveBatches(list) {
        localStorage.setItem('agri_traceability_lots', JSON.stringify(list));
    }

    let allBatches = getBatches();
    let currentBatch = allBatches[0];

    // DOM Elements
    const quickLotChipsContainer = document.getElementById('quickLotChipsContainer');
    const traceSearchInput = document.getElementById('traceSearchInput');
    const btnClearSearch = document.getElementById('btnClearSearch');
    const seasonFilterSelect = document.getElementById('seasonFilterSelect');
    const certFilterSelect = document.getElementById('certFilterSelect');
    const traceTabsNav = document.getElementById('traceTabsNav');
    const batchDossierContainer = document.getElementById('batchDossierContainer');
    const traceEmptyState = document.getElementById('traceEmptyState');
    const btnResetTraceFilters = document.getElementById('btnResetTraceFilters');

    // KPI Elements
    const kpiTotalLots = document.getElementById('kpiTotalLots');
    const kpiTotalYield = document.getElementById('kpiTotalYield');

    // Detail Product Hero Elements
    const detailProductImg = document.getElementById('detailProductImg');
    const detailBatchTag = document.getElementById('detailBatchTag');
    const detailCertBadge = document.getElementById('detailCertBadge');
    const detailSeasonBadge = document.getElementById('detailSeasonBadge');
    const detailProductName = document.getElementById('detailProductName');
    const detailProductStory = document.getElementById('detailProductStory');
    const detailVariety = document.getElementById('detailVariety');
    const detailCropDuration = document.getElementById('detailCropDuration');
    const detailFarmLocation = document.getElementById('detailFarmLocation');
    const detailPucCode = document.getElementById('detailPucCode');
    const detailGps = document.getElementById('detailGps');
    const detailManager = document.getElementById('detailManager');
    const detailQrContainer = document.getElementById('detailQrContainer');
    const btnDownloadQr = document.getElementById('btnDownloadQr');
    const btnCopyTraceLink = document.getElementById('btnCopyTraceLink');

    // Stepper & Activities
    const journeyStepperContainer = document.getElementById('journeyStepperContainer');
    const traceActivityTimeline = document.getElementById('traceActivityTimeline');
    const activityCategoryFilter = document.getElementById('activityCategoryFilter');

    // Fertilizer & Pesticides
    const fertilizerTableBody = document.getElementById('fertilizerTableBody');
    const pesticideTableBody = document.getElementById('pesticideTableBody');
    const residueTestSummary = document.getElementById('residueTestSummary');

    // Harvest Records
    const harvestActualTime = document.getElementById('harvestActualTime');
    const harvestActualYield = document.getElementById('harvestActualYield');
    const harvestGradeRatio = document.getElementById('harvestGradeRatio');
    const harvestInspector = document.getElementById('harvestInspector');
    const harvestPackaging = document.getElementById('harvestPackaging');
    const harvestStorage = document.getElementById('harvestStorage');

    // Mobile Preview Elements
    const mobileProductName = document.getElementById('mobileProductName');
    const mobileBatchCode = document.getElementById('mobileBatchCode');
    const mobileProductImg = document.getElementById('mobileProductImg');
    const mobileLocation = document.getElementById('mobileLocation');
    const mobilePuc = document.getElementById('mobilePuc');
    const mobileHarvestDate = document.getElementById('mobileHarvestDate');
    const mobileCertStandard = document.getElementById('mobileCertStandard');
    const mobileJourneyList = document.getElementById('mobileJourneyList');

    // Modals
    const scanModal = document.getElementById('scanModal');
    const btnOpenScanModal = document.getElementById('btnOpenScanModal');
    const btnCloseScanModal = document.getElementById('btnCloseScanModal');
    const btnCancelScan = document.getElementById('btnCancelScan');

    const newBatchModal = document.getElementById('newBatchModal');
    const btnNewBatchModal = document.getElementById('btnNewBatchModal');
    const btnCloseNewBatchModal = document.getElementById('btnCloseNewBatchModal');
    const btnCancelNewBatch = document.getElementById('btnCancelNewBatch');
    const newBatchForm = document.getElementById('newBatchForm');

    const certificateModal = document.getElementById('certificateModal');
    const btnPrintCertBtn = document.getElementById('btnPrintCertBtn');
    const btnCloseCertModal = document.getElementById('btnCloseCertModal');
    const btnCloseCertAction = document.getElementById('btnCloseCertAction');

    // Certificate Print Fields
    const certProductName = document.getElementById('certProductName');
    const certBatchId = document.getElementById('certBatchId');
    const certVariety = document.getElementById('certVariety');
    const certSeason = document.getElementById('certSeason');
    const certPuc = document.getElementById('certPuc');
    const certStandard = document.getElementById('certStandard');
    const certHarvestDate = document.getElementById('certHarvestDate');
    const certYield = document.getElementById('certYield');
    const certQrBox = document.getElementById('certQrBox');
    const certIssueDate = document.getElementById('certIssueDate');
    const certDirector = document.getElementById('certDirector');

    // SVG QR Code Generator (Works 100% offline and standalone)
    function generateQrSvg(text, size = 130) {
        // Simple and elegant pseudo-random deterministic QR matrix based on hash of text
        let hash = 0;
        for (let i = 0; i < text.length; i++) {
            hash = ((hash << 5) - hash) + text.charCodeAt(i);
            hash |= 0;
        }

        const matrixSize = 25;
        const cellSize = size / matrixSize;
        let rects = '';

        // Fixed finder patterns at 3 corners
        function drawFinder(startX, startY) {
            let s = '';
            for (let r = 0; r < 7; r++) {
                for (let c = 0; c < 7; c++) {
                    const isBorder = (r === 0 || r === 6 || c === 0 || c === 6);
                    const isInner = (r >= 2 && r <= 4 && c >= 2 && c <= 4);
                    if (isBorder || isInner) {
                        s += `<rect x="${(startX + c) * cellSize}" y="${(startY + r) * cellSize}" width="${cellSize}" height="${cellSize}" fill="#0c2920"/>`;
                    }
                }
            }
            return s;
        }

        rects += drawFinder(1, 1);
        rects += drawFinder(matrixSize - 8, 1);
        rects += drawFinder(1, matrixSize - 8);

        // Fill remaining data cells with deterministic pseudorandom pattern based on hash
        let seed = Math.abs(hash);
        for (let r = 1; r < matrixSize - 1; r++) {
            for (let c = 1; c < matrixSize - 1; c++) {
                // Skip finder areas
                const inTopLeft = (r <= 8 && c <= 8);
                const inTopRight = (r <= 8 && c >= matrixSize - 9);
                const inBottomLeft = (r >= matrixSize - 9 && c <= 8);
                if (inTopLeft || inTopRight || inBottomLeft) continue;

                seed = (seed * 9301 + 49297) % 233280;
                const rnd = seed / 233280;
                if (rnd > 0.48) {
                    rects += `<rect x="${c * cellSize}" y="${r * cellSize}" width="${cellSize}" height="${cellSize}" fill="#0c2920"/>`;
                }
            }
        }

        return `
            <svg width="${size}" height="${size}" viewBox="0 0 ${size} ${size}" xmlns="http://www.w3.org/2000/svg">
                <rect width="${size}" height="${size}" fill="#ffffff" rx="8"/>
                ${rects}
                <rect x="${size / 2 - 12}" y="${size / 2 - 12}" width="24" height="24" fill="#ffffff" rx="4"/>
                <circle cx="${size / 2}" cy="${size / 2}" r="9" fill="#10b981"/>
                <path d="M ${size / 2 - 4} ${size / 2} L ${size / 2 - 1} ${size / 2 + 3} L ${size / 2 + 4} ${size / 2 - 3}" stroke="#ffffff" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
        `;
    }

    // Render Quick Lot Chips
    function renderQuickLotChips() {
        quickLotChipsContainer.innerHTML = '';
        allBatches.forEach(batch => {
            const btn = document.createElement('button');
            btn.className = `lot-chip-btn ${currentBatch && currentBatch.id === batch.id ? 'active' : ''}`;
            btn.innerHTML = `
                <span>${batch.productName}</span>
                <span class="lot-badge-code">${batch.id}</span>
                <span style="font-size:11px; color:${batch.certification === 'GlobalGAP' ? '#0284c7' : '#10b981'}">• ${batch.certification}</span>
            `;
            btn.addEventListener('click', () => {
                selectBatch(batch.id);
            });
            quickLotChipsContainer.appendChild(btn);
        });

        // Update KPIs
        if (kpiTotalLots) kpiTotalLots.textContent = allBatches.length;
    }

    // Select and Bind Active Batch
    function selectBatch(batchId) {
        const found = allBatches.find(b => b.id === batchId);
        if (!found) return;

        currentBatch = found;
        renderQuickLotChips();
        bindBatchDetails(found);
    }

    // Bind Batch Data to DOM
    function bindBatchDetails(b) {
        batchDossierContainer.style.display = 'block';
        traceEmptyState.style.display = 'none';

        // 1. Hero
        detailProductImg.src = b.productImg;
        detailProductImg.alt = b.productName;
        detailBatchTag.textContent = `Mã Lô: ${b.id}`;

        detailCertBadge.innerHTML = `<i class="fa-solid fa-certificate"></i> ${b.certification} (Số: ${b.certNumber || 'Chính thức'})`;
        detailSeasonBadge.innerHTML = `<i class="fa-solid fa-snowflake"></i> ${b.season}`;

        detailProductName.textContent = b.productName;
        detailProductStory.textContent = b.productStory;

        detailVariety.textContent = b.variety;
        detailCropDuration.textContent = b.cropDuration;
        detailFarmLocation.textContent = b.farmLocation;
        detailPucCode.textContent = b.pucCode;
        detailGps.textContent = b.gpsCoordinates;
        detailManager.textContent = b.manager;

        // QR Code
        const qrSvg = generateQrSvg(`https://agrimanage.vn/trace/${b.id}`, 130);
        detailQrContainer.innerHTML = qrSvg;

        // 2. Journey Stepper
        journeyStepperContainer.innerHTML = '';
        if (b.journey && b.journey.length > 0) {
            b.journey.forEach(step => {
                const card = document.createElement('div');
                card.className = `journey-step-card ${step.status === 'completed' ? 'completed' : 'active'}`;
                card.innerHTML = `
                    <div class="journey-step-header">
                        <span class="step-num-badge">${step.step}</span>
                        <span style="font-size:11px; font-weight:700; color:${step.status === 'completed' ? 'var(--primary)' : 'var(--accent-orange)'}">
                            <i class="fa-solid fa-check"></i> ${step.status === 'completed' ? 'Đạt chuẩn' : 'Tiến hành'}
                        </span>
                    </div>
                    <div class="journey-step-title">${step.title}</div>
                    <div class="journey-step-date"><i class="fa-regular fa-calendar"></i> ${step.date}</div>
                    <div class="journey-step-desc">${step.desc}</div>
                `;
                journeyStepperContainer.appendChild(card);
            });
        }

        // 3. Farming Activities Timeline
        renderActivityTimeline(b.activities);

        // 4. Fertilizers Table
        fertilizerTableBody.innerHTML = '';
        if (b.fertilizers && b.fertilizers.length > 0) {
            b.fertilizers.forEach(f => {
                const tr = document.createElement('tr');
                tr.innerHTML = `
                    <td><strong>${f.date}</strong></td>
                    <td><strong style="color:var(--text-main);">${f.name}</strong></td>
                    <td><span class="badge" style="background:#ecfdf5; color:#047857; font-weight:700; font-size:11px;">${f.type}</span></td>
                    <td>${f.dose}</td>
                    <td>${f.stage}</td>
                    <td><i class="fa-solid fa-user-gear text-teal"></i> ${f.operator}</td>
                    <td><span class="badge" style="background:#dcfce7; color:#15803d; font-weight:700; font-size:11px;"><i class="fa-solid fa-circle-check"></i> ${f.status || 'Hoàn thành'}</span></td>
                `;
                fertilizerTableBody.appendChild(tr);
            });
        } else {
            fertilizerTableBody.innerHTML = `<tr><td colspan="7" style="text-align:center; color:var(--text-muted);">Không có ghi nhận bón phân hóa học.</td></tr>`;
        }

        // 4.2 Pesticides Table
        pesticideTableBody.innerHTML = '';
        if (b.pesticides && b.pesticides.length > 0) {
            b.pesticides.forEach(p => {
                const tr = document.createElement('tr');
                tr.innerHTML = `
                    <td><strong>${p.date}</strong></td>
                    <td><strong style="color:var(--text-main);">${p.name}</strong></td>
                    <td><span style="font-family:monospace; font-size:12px; color:var(--text-muted);">${p.active}</span></td>
                    <td>${p.target}</td>
                    <td>${p.dose}</td>
                    <td><span class="badge" style="background:#fffbeb; color:#b45309; font-weight:700; font-size:11.5px;"><i class="fa-regular fa-clock"></i> Cách ly: ${p.phiDays} ngày</span></td>
                    <td><span class="phi-safety-pill phi-safe"><i class="fa-solid fa-shield-check"></i> ${p.verdict}</span></td>
                    <td><i class="fa-solid fa-user-shield text-primary"></i> ${p.operator}</td>
                `;
                pesticideTableBody.appendChild(tr);
            });
        } else {
            pesticideTableBody.innerHTML = `<tr><td colspan="8" style="text-align:center; color:var(--text-muted);">Không sử dụng thuốc BVTV hóa học. Canh tác an toàn 100%.</td></tr>`;
        }

        // Residue testing note
        if (residueTestSummary) {
            residueTestSummary.innerHTML = `Phiếu kiểm nghiệm số: <strong>TEST-VN-2026-${b.id.slice(-4)}</strong> do Trung tâm Tiêu chuẩn Kỹ thuật Đo lường Chất lượng Quatest cấp chứng thực.`;
        }

        // 5. Harvest Records
        harvestActualTime.innerHTML = `<i class="fa-solid fa-clock text-primary"></i> ${b.harvestActualTime || '20/03/2026 - 08:30'}`;
        harvestActualYield.innerHTML = `<i class="fa-solid fa-weight-scale text-primary"></i> ${b.harvestActualYield}`;
        harvestGradeRatio.textContent = b.harvestGradeRatio || 'Loại 1: 96% | Loại 2: 4%';
        harvestInspector.innerHTML = `<i class="fa-solid fa-user-check text-primary"></i> ${b.inspector}`;
        harvestPackaging.textContent = b.packaging;
        harvestStorage.textContent = b.storageCondition;

        // 6. Mobile Preview Screen
        mobileProductName.textContent = b.productName;
        mobileBatchCode.textContent = b.id;
        mobileProductImg.src = b.productImg;
        mobileLocation.textContent = b.farmLocation;
        mobilePuc.textContent = `Mã vùng trồng: ${b.pucCode}`;
        mobileHarvestDate.textContent = b.harvestActualTime ? b.harvestActualTime.split(' - ')[0] : '20/03/2026';
        mobileCertStandard.textContent = `${b.certification} Tiêu chuẩn`;

        mobileJourneyList.innerHTML = '';
        if (b.journey && b.journey.length > 0) {
            b.journey.forEach(step => {
                const li = document.createElement('li');
                li.innerHTML = `<strong>${step.title}</strong> (${step.date}): ${step.desc}`;
                mobileJourneyList.appendChild(li);
            });
        }
    }

    // Render Activity Timeline
    function renderActivityTimeline(activities) {
        traceActivityTimeline.innerHTML = '';
        if (!activities || activities.length === 0) {
            traceActivityTimeline.innerHTML = `<div style="color:var(--text-muted); padding:10px 0;">Chưa có hoạt động canh tác nào được ghi nhận.</div>`;
            return;
        }

        const catFilter = activityCategoryFilter ? activityCategoryFilter.value : '';
        const filtered = catFilter ? activities.filter(a => a.category === catFilter) : activities;

        filtered.forEach(act => {
            const node = document.createElement('div');
            node.className = 'trace-timeline-node';
            
            let iconClass = 'fa-check';
            if (act.category === 'Làm đất') iconClass = 'fa-tractor';
            else if (act.category === 'Gieo trồng') iconClass = 'fa-seedling';
            else if (act.category === 'Bón phân') iconClass = 'fa-leaf';
            else if (act.category === 'Tưới nước') iconClass = 'fa-droplet';
            else if (act.category === 'Phun thuốc') iconClass = 'fa-spray-can-sparkles';
            else if (act.category === 'Thu hoạch') iconClass = 'fa-wheat-awn';

            node.innerHTML = `
                <div class="trace-timeline-dot">
                    <i class="fa-solid ${iconClass}"></i>
                </div>
                <div class="trace-timeline-card">
                    <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:6px; flex-wrap:wrap; gap:6px;">
                        <div>
                            <span class="badge" style="background:var(--primary-light); color:var(--primary-hover); font-weight:700; font-size:11.5px; margin-right:6px;">
                                ${act.category}
                            </span>
                            <span style="font-size:12.5px; color:var(--text-muted); font-weight:600;">
                                <i class="fa-regular fa-calendar"></i> ${act.date}
                            </span>
                        </div>
                        <span class="badge" style="background:#dcfce7; color:#15803d; font-weight:700; font-size:11.5px;">
                            <i class="fa-solid fa-circle-check"></i> ${act.status}
                        </span>
                    </div>

                    <h4 style="font-size:14.5px; font-weight:700; color:var(--text-main); margin-bottom:4px;">
                        ${act.title}
                    </h4>

                    <div style="font-size:13px; color:var(--text-main); margin-bottom:6px;">
                        ${act.notes || ''}
                    </div>

                    <div style="font-size:12px; color:var(--text-muted);">
                        <i class="fa-solid fa-user text-primary"></i> Người thực hiện: <strong>${act.operator}</strong>
                    </div>
                </div>
            `;
            traceActivityTimeline.appendChild(node);
        });
    }

    // Filter Activities Handler
    if (activityCategoryFilter) {
        activityCategoryFilter.addEventListener('change', () => {
            if (currentBatch) renderActivityTimeline(currentBatch.activities);
        });
    }

    // Search and Filter Handling
    function handleSearchFilter() {
        const query = traceSearchInput.value.trim().toLowerCase();
        const season = seasonFilterSelect.value;
        const cert = certFilterSelect.value;

        if (query) {
            btnClearSearch.style.display = 'inline-flex';
        } else {
            btnClearSearch.style.display = 'none';
        }

        const matched = allBatches.filter(b => {
            const matchQuery = !query || 
                b.id.toLowerCase().includes(query) ||
                b.productName.toLowerCase().includes(query) ||
                b.variety.toLowerCase().includes(query) ||
                b.farmLocation.toLowerCase().includes(query) ||
                b.pucCode.toLowerCase().includes(query);

            const matchSeason = !season || b.season === season;
            const matchCert = !cert || b.certification.toLowerCase().includes(cert.toLowerCase());

            return matchQuery && matchSeason && matchCert;
        });

        if (matched.length > 0) {
            selectBatch(matched[0].id);
        } else {
            batchDossierContainer.style.display = 'none';
            traceEmptyState.style.display = 'block';
        }
    }

    traceSearchInput.addEventListener('input', handleSearchFilter);
    seasonFilterSelect.addEventListener('change', handleSearchFilter);
    certFilterSelect.addEventListener('change', handleSearchFilter);

    btnClearSearch.addEventListener('click', () => {
        traceSearchInput.value = '';
        handleSearchFilter();
    });

    btnResetTraceFilters.addEventListener('click', () => {
        traceSearchInput.value = '';
        seasonFilterSelect.value = '';
        certFilterSelect.value = '';
        handleSearchFilter();
    });

    // Tab Navigation for Sections
    const tabButtons = traceTabsNav.querySelectorAll('.trace-tab-item');
    tabButtons.forEach(btn => {
        btn.addEventListener('click', () => {
            tabButtons.forEach(b => b.classList.remove('active'));
            btn.classList.add('active');

            const tab = btn.getAttribute('data-tab');
            const sections = [
                document.getElementById('section-season'),
                document.getElementById('section-activities'),
                document.getElementById('section-chemicals'),
                document.getElementById('section-harvest'),
                document.getElementById('section-consumer')
            ];

            if (tab === 'tab-all') {
                sections.forEach(s => { if (s) s.style.display = ''; });
            } else if (tab === 'tab-season') {
                sections.forEach(s => { if (s) s.style.display = (s.id === 'section-season' ? '' : 'none'); });
            } else if (tab === 'tab-activities') {
                sections.forEach(s => { if (s) s.style.display = (s.id === 'section-activities' ? '' : 'none'); });
            } else if (tab === 'tab-chemicals') {
                sections.forEach(s => { if (s) s.style.display = (s.id === 'section-chemicals' ? '' : 'none'); });
            } else if (tab === 'tab-harvest') {
                sections.forEach(s => { if (s) s.style.display = (s.id === 'section-harvest' ? '' : 'none'); });
            } else if (tab === 'tab-consumer') {
                sections.forEach(s => { if (s) s.style.display = (s.id === 'section-consumer' ? '' : 'none'); });
            }
        });
    });

    // Copy Link Action
    btnCopyTraceLink.addEventListener('click', () => {
        const link = `https://agrimanage.vn/trace/${currentBatch.id}`;
        navigator.clipboard.writeText(link).then(() => {
            if (window.showToast) window.showToast('Sao chép thành công', `Đã chép liên kết truy xuất cho ${currentBatch.id}`, 'success');
            else alert(`Đã sao chép liên kết: ${link}`);
        });
    });

    // Download QR Action
    btnDownloadQr.addEventListener('click', () => {
        const svgElement = detailQrContainer.querySelector('svg');
        if (!svgElement) return;

        const svgData = new XMLSerializer().serializeToString(svgElement);
        const canvas = document.createElement('canvas');
        const ctx = canvas.getContext('2d');
        const img = new Image();
        canvas.width = 400;
        canvas.height = 400;

        img.onload = () => {
            ctx.fillStyle = '#ffffff';
            ctx.fillRect(0, 0, 400, 400);
            ctx.drawImage(img, 20, 20, 360, 360);
            const a = document.createElement('a');
            a.download = `QR_${currentBatch.id}.png`;
            a.href = canvas.toDataURL('image/png');
            a.click();
            if (window.showToast) window.showToast('Tải mã QR', `Đã tải tem mã QR cho lô ${currentBatch.id}`, 'success');
        };
        img.src = 'data:image/svg+xml;base64,' + btoa(unescape(encodeURIComponent(svgData)));
    });

    // Modal: Scan QR Code
    btnOpenScanModal.addEventListener('click', () => scanModal.classList.add('active'));
    btnCloseScanModal.addEventListener('click', () => scanModal.classList.remove('active'));
    btnCancelScan.addEventListener('click', () => scanModal.classList.remove('active'));

    window.simulateScanLot = function(lotId) {
        scanModal.classList.remove('active');
        selectBatch(lotId);
        if (window.showToast) {
            window.showToast('Quét mã thành công!', `Đã nhận diện mã lô: ${lotId}`, 'success');
        }
    };

    // Modal: Register New Batch
    btnNewBatchModal.addEventListener('click', () => {
        newBatchForm.reset();
        document.getElementById('newBatchId').value = `LOT-2026-NEW-0${allBatches.length + 1}`;
        newBatchModal.classList.add('active');
    });

    btnCloseNewBatchModal.addEventListener('click', () => newBatchModal.classList.remove('active'));
    btnCancelNewBatch.addEventListener('click', () => newBatchModal.classList.remove('active'));

    newBatchForm.addEventListener('submit', (e) => {
        e.preventDefault();

        const id = document.getElementById('newBatchId').value.trim().toUpperCase();
        const productName = document.getElementById('newProductName').value.trim();
        const variety = document.getElementById('newVariety').value.trim();
        const season = document.getElementById('newSeason').value;
        const farmLocation = document.getElementById('newFarmLocation').value.trim();
        const pucCode = document.getElementById('newPucCode').value.trim().toUpperCase();
        const harvestDate = document.getElementById('newHarvestDate').value;
        const harvestQty = document.getElementById('newHarvestQty').value.trim();
        const certification = document.getElementById('newCertification').value;
        const inspector = document.getElementById('newInspector').value.trim() || 'Trần Văn Lượng';
        const productImg = document.getElementById('newProductImg').value.trim() || 'https://images.unsplash.com/photo-1550258987-190a2d41a8ba?auto=format&fit=crop&w=600&q=80';
        const description = document.getElementById('newDescription').value.trim() || 'Nông sản an toàn canh tác theo tiêu chuẩn chất lượng cao.';

        // Create new Batch Object
        const newBatch = {
            id,
            productName,
            variety,
            season,
            cropDuration: `${harvestDate} (Vừa thu hoạch)`,
            farmLocation,
            gpsCoordinates: '11°56\'00.0"N 108°26\'00.0"E',
            pucCode,
            certification,
            certNumber: `${certification}-VN-26-${Math.floor(1000 + Math.random() * 9000)}`,
            manager: 'Trần Văn Lượng (Quản lý vườn)',
            inspector,
            productImg,
            productStory: description,
            harvestActualTime: `${harvestDate} - 08:00`,
            harvestActualYield: harvestQty,
            harvestGradeRatio: 'Loại 1: 95% | Loại 2: 5%',
            packaging: 'Đóng thùng tiêu chuẩn có dán tem QR truy xuất nguồn gốc độc bản.',
            storageCondition: 'Nhiệt độ phòng mát 15-20°C. HSD: 30 ngày.',
            journey: [
                { step: 1, title: 'Làm đất & Xử lý giống', date: harvestDate, desc: 'Đất canh tác được kiểm định không tồn dư kim loại nặng.', status: 'completed' },
                { step: 2, title: 'Gieo cấy theo quy chuẩn', date: harvestDate, desc: 'Hạt giống đạt chuẩn F1 nguồn gốc rõ ràng.', status: 'completed' },
                { step: 3, title: 'Chăm sóc hữu cơ sinh học', date: harvestDate, desc: 'Bón phân hữu cơ vi sinh và kiểm soát dịch hại tự nhiên.', status: 'completed' },
                { step: 4, title: 'Cách ly an toàn PHI', date: harvestDate, desc: 'Tuân thủ thời gian cách ly trước thu hoạch 100%.', status: 'completed' },
                { step: 5, title: 'Thu hoạch & Đóng gói dán tem', date: harvestDate, desc: 'Nghiệm thu đạt chuẩn và niêm phong mã QR truy xuất.', status: 'completed' }
            ],
            activities: [
                { date: harvestDate, category: 'Làm đất', title: 'Khử khuẩn và chuẩn bị vùng trồng đạt chuẩn', operator: inspector, status: 'Hoàn thành (100%)', notes: 'Ghi nhận bắt đầu chu kỳ canh tác' },
                { date: harvestDate, category: 'Thu hoạch', title: 'Thu hoạch và cấp chứng nhận truy xuất', operator: inspector, status: 'Hoàn thành (100%)', notes: `Sản lượng đạt ${harvestQty}` }
            ],
            fertilizers: [
                { date: harvestDate, name: 'Phân bón hữu cơ sinh học Quế Lâm', type: 'Hữu cơ', dose: '300 kg/ha', stage: 'Bón lót', operator: inspector, status: 'Đã hoàn thành' }
            ],
            pesticides: [
                { date: harvestDate, name: 'Chế phẩm thảo mộc sinh học an toàn', active: 'Thảo mộc tự nhiên', target: 'Phòng trừ côn trùng', dose: '1.0 lít/ha', phiDays: 0, phiSafe: true, safeDate: harvestDate, operator: inspector, verdict: 'ĐẠT AN TOÀN TUYỆT ĐỐI' }
            ]
        };

        allBatches.unshift(newBatch);
        saveBatches(allBatches);
        newBatchModal.classList.remove('active');
        selectBatch(newBatch.id);

        if (window.addFarmNotification) {
            window.addFarmNotification(
                `ĐÃ CẤP MÃ TRUY XUẤT: ${id}`,
                `Lô nông sản "${productName}" (${harvestQty}) đã được kích hoạt mã QR truy xuất nguồn gốc VietGAP/GlobalGAP.`,
                'success'
            );
        } else if (window.showToast) {
            window.showToast('Đăng ký thành công', `Đã cấp mã truy xuất ${id} cho ${productName}`, 'success');
        }
    });

    // Modal: Certificate of Origin & Print
    btnPrintCertBtn.addEventListener('click', () => {
        if (!currentBatch) return;

        certProductName.textContent = currentBatch.productName;
        certBatchId.textContent = currentBatch.id;
        certVariety.textContent = currentBatch.variety;
        certSeason.textContent = currentBatch.season;
        certPuc.textContent = currentBatch.pucCode;
        certStandard.textContent = `${currentBatch.certification} (${currentBatch.certNumber || 'Đạt chuẩn'})`;
        certHarvestDate.textContent = currentBatch.harvestActualTime ? currentBatch.harvestActualTime.split(' - ')[0] : '20/03/2026';
        certYield.textContent = currentBatch.harvestActualYield;
        certIssueDate.textContent = new Date().toLocaleDateString('vi-VN');
        certDirector.textContent = currentBatch.manager;

        certQrBox.innerHTML = generateQrSvg(`https://agrimanage.vn/trace/${currentBatch.id}`, 90);

        certificateModal.classList.add('active');
    });

    btnCloseCertModal.addEventListener('click', () => certificateModal.classList.remove('active'));
    btnCloseCertAction.addEventListener('click', () => certificateModal.classList.remove('active'));

    // Close modals on clicking overlay backdrop or pressing Escape
    [scanModal, newBatchModal, certificateModal].forEach(modal => {
        if (!modal) return;
        modal.addEventListener('click', (e) => {
            if (e.target === modal) {
                modal.classList.remove('active');
            }
        });
    });

    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape') {
            if (scanModal) scanModal.classList.remove('active');
            if (newBatchModal) newBatchModal.classList.remove('active');
            if (certificateModal) certificateModal.classList.remove('active');
        }
    });

    // Open Mobile Public Modal preview button
    const btnPreviewPublicModal = document.getElementById('btnPreviewPublicModal');
    if (btnPreviewPublicModal) {
        btnPreviewPublicModal.addEventListener('click', () => {
            const previewTab = traceTabsNav.querySelector('[data-tab="tab-consumer"]');
            if (previewTab) previewTab.click();
            const mobileSection = document.getElementById('section-consumer');
            if (mobileSection) {
                mobileSection.scrollIntoView({ behavior: 'smooth' });
            }
        });
    }

    // Initial render
    renderQuickLotChips();
    selectBatch(allBatches[0].id);
});
