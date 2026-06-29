-- ========================================================
-- SCRIPT: INSERT MOCK DATA FOR PROJECT MT01 (PART_ID: Numbers Only)
-- ========================================================
USE V2_KCOP;
GO

-- 1. เพิ่มข้อมูลตำแหน่งจัดเก็บ (MT_LOCATION)
-- ถอดรหัส 4 หลัก: [Rack][Floor][Slot][Block]
INSERT INTO MT_LOCATION (LOC_ID, RACK, FLOOR, SLOT, BLOCK, DESCRIPTION, IS_ACTIVE) VALUES
('A100', 'A', 1, 0, 0, N'Rack A ชั้น 1 ช่อง 0 (เต็มช่อง)', 1),
('A110', 'A', 1, 1, 0, N'Rack A ชั้น 1 ช่อง 1 (เต็มช่อง)', 1),
('A251', 'A', 2, 5, 1, N'Rack A ชั้น 2 ช่อง 5 บล็อกย่อย 1', 1),
('A252', 'A', 2, 5, 2, N'Rack A ชั้น 2 ช่อง 5 บล็อกย่อย 2', 1),
('B3A0', 'B', 3, 10, 0, N'Rack B ชั้น 3 ช่อง 10 (A) (เต็มช่อง)', 1),
('C120', 'C', 1, 2, 0, N'Rack C ชั้น 1 ช่อง 2 (เต็มช่อง)', 1);


-- 2. เพิ่มข้อมูลรายการอะไหล่และอุปกรณ์ (MT_PART_ITEM)
-- ✅ ปรับ PART_ID เป็นตัวเลขล้วน (เหมาะสำหรับฝังใน QR Code ขนาดกะทัดรัด)
INSERT INTO MT_PART_ITEM (PART_ID, PART_CODE, PART_NAME, BRAND, MODEL, CATEGORY, UNIT, MIN_STOCK, DESCRIPTION, IS_ACTIVE) VALUES
('0001', 'BRG-6205', N'ตลับลูกปืน (Bearing) 6205', 'NSK', '6205-ZZ', 'SPARE', 'PCS', 10.00, N'ใช้กับ Motor Line 1', 1),
('0002', 'INV-YAS-01', N'Inverter 2.2KW', 'Yaskawa', 'CIMR-VA4A0009BAA', 'EQUIP', 'PCS', 2.00, N'เครื่องสำรองสำหรับ Line 2', 1),
('0003', 'SCR-M6-20', N'สกรูหัวเหลี่ยม M6 x 20', 'Generic', 'M6-20', 'SPARE', 'PCS', 100.00, N'สกรูสแตนเลส 304', 1),
('0004', 'SCR-M6-30', N'สกรูหัวเหลี่ยม M6 x 30', 'Generic', 'M6-30', 'SPARE', 'PCS', 100.00, N'สกรูสแตนเลส 304', 1),
('0005', 'TL-WRE-17', N'ประแจแหวนข้างปากตาย เบอร์ 17', 'Koken', 'K-17', 'TOOL', 'PCS', 1.00, N'เครื่องมือประจำช็อปซ่อมบำรุง', 1);


-- 3. เพิ่มข้อมูลคู่ค้า (MT_PART_SUPPLIER)
INSERT INTO MT_PART_SUPPLIER (PART_ID, SUP_ID, PART_NO_SUP, REMARK) VALUES
('0001', 'SUP-KCOP-001', 'NSK-6205ZZ-X', N'จัดซื้อจาก บจก.แบริ่งไทย'),
('0002', 'SUP-KCOP-002', 'YAS-INV-2.2', N'สั่งซื้อผ่านตัวแทนจำหน่ายหลัก');


-- 4. เพิ่มข้อมูลสต็อกคงคลัง (MT_STOCK)
INSERT INTO MT_STOCK (PART_ID, LOC_ID, QTY_ON_HAND, LAST_UPDATED) VALUES
('0001', 'A100', 15.00, GETDATE()),  
('0002', 'B3A0', 1.00, GETDATE()),   
('0003', 'A251', 150.00, GETDATE()), 
('0004', 'A252', 80.00, GETDATE()),  
('0005', 'C120', 3.00, GETDATE());   


-- 5. เพิ่มประวัติรายการ (MT_TXN) และบันทึก Audit Log
DECLARE @TxnID_1 INT, @TxnID_2 INT, @TxnID_3 INT;

-- เคสที่ 1: IN (รับของเข้า)
INSERT INTO MT_TXN (TXN_TYPE, PART_ID, LOC_ID, LOC_FROM, LOC_TO, QTY, TXN_DATE, REMARK, CREATED_BY, CREATED_AT, DEVICE_INFO) VALUES
('IN', '0001', 'A100', NULL, 'A100', 15.00, CAST(GETDATE() AS DATE), N'รับเข้าสต็อกเริ่มต้น ยอดเปิดระบบ', 'admin', GETDATE(), 'Handheld-01');
SET @TxnID_1 = SCOPE_IDENTITY();

-- เคสที่ 2: OUT (เบิกของออก)
INSERT INTO MT_TXN (TXN_TYPE, PART_ID, LOC_ID, LOC_FROM, LOC_TO, QTY, TXN_DATE, REMARK, CREATED_BY, CREATED_AT, DEVICE_INFO) VALUES
('OUT', '0004', 'A252', 'A252', NULL, 20.00, CAST(GETDATE() AS DATE), N'เบิกไปซ่อมเครื่องจักรจักร Line 1', 'technician_01', GETDATE(), 'Web-Browser');
SET @TxnID_2 = SCOPE_IDENTITY();

-- เคสที่ 3: MOV (ย้ายตำแหน่ง)
INSERT INTO MT_TXN (TXN_TYPE, PART_ID, LOC_ID, LOC_FROM, LOC_TO, QTY, TXN_DATE, REMARK, CREATED_BY, CREATED_AT, DEVICE_INFO) VALUES
('MOV', '0002', 'B3A0', 'A110', 'B3A0', 1.00, CAST(GETDATE() AS DATE), N'ย้ายมิกเซอร์จากแร็ค A ไปแร็ค B ช่อง 10', 'admin', GETDATE(), 'Handheld-02');
SET @TxnID_3 = SCOPE_IDENTITY();


-- 6. เพิ่มข้อมูล Audit Log (MT_AUDIT_LOG)
INSERT INTO MT_AUDIT_LOG (TXN_ID, ACTION, CHANGED_BY, CHANGED_AT, OLD_VALUE, NEW_VALUE, DEVICE_INFO) VALUES
(@TxnID_1, 'CREATE', 'admin', GETDATE(), NULL, '{"QTY":15.00,"LOC_TO":"A100"}', 'Handheld-01'),
(@TxnID_2, 'CREATE', 'technician_01', GETDATE(), NULL, '{"QTY":20.00,"LOC_FROM":"A252"}', 'Web-Browser'),
(@TxnID_3, 'CREATE', 'admin', GETDATE(), NULL, '{"QTY":1.00,"LOC_FROM":"A110","LOC_TO":"B3A0"}', 'Handheld-02');

PRINT '=== Mock Data (Numeric PART_ID) Inserted Successfully ===';