-- ============================================================
-- MT01 Inventory System
-- Database : V2_KCOP
-- Script   : สร้างตาราง MT_ ทั้งหมด
-- ============================================================

USE V2_KCOP;
GO

-- ------------------------------------------------------------
-- MT_PART_ITEM
-- ------------------------------------------------------------
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'MT_PART_ITEM')
BEGIN
    CREATE TABLE MT_PART_ITEM (
        PART_ID       NVARCHAR(20)   NOT NULL,
        PART_CODE     NVARCHAR(20)   NOT NULL,
        PART_NAME     NVARCHAR(100)  NOT NULL,
        BRAND         NVARCHAR(50)   NULL,
        MODEL         NVARCHAR(100)  NULL,
        CATEGORY      NVARCHAR(10)   NOT NULL,
        UNIT          NVARCHAR(10)   NOT NULL,
        MIN_STOCK     DECIMAL(12,2)  NOT NULL DEFAULT 0,
        DESCRIPTION   NVARCHAR(500)  NULL,
        IS_ACTIVE     BIT            NOT NULL DEFAULT 1,

        CONSTRAINT PK_MT_PART_ITEM   PRIMARY KEY (PART_ID),
        CONSTRAINT UQ_MT_PART_CODE   UNIQUE (PART_CODE),
        CONSTRAINT CK_MT_CATEGORY    CHECK (CATEGORY IN ('SPARE','EQUIP','TOOL'))
    );
    PRINT 'Created: MT_PART_ITEM';
END
GO

-- ------------------------------------------------------------
-- MT_LOCATION
-- ------------------------------------------------------------
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'MT_LOCATION')
BEGIN
    CREATE TABLE MT_LOCATION (
        LOC_ID        NVARCHAR(10)   NOT NULL,
        RACK          NCHAR(1)       NOT NULL,
        FLOOR         TINYINT        NOT NULL,
        SLOT          TINYINT        NOT NULL,
        BLOCK         TINYINT        NOT NULL DEFAULT 0,
        DESCRIPTION   NVARCHAR(100)  NULL,
        IS_ACTIVE     BIT            NOT NULL DEFAULT 1,

        CONSTRAINT PK_MT_LOCATION    PRIMARY KEY (LOC_ID)
    );
    PRINT 'Created: MT_LOCATION';
END
GO

-- ------------------------------------------------------------
-- MT_PART_SUPPLIER
-- ------------------------------------------------------------
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'MT_PART_SUPPLIER')
BEGIN
    CREATE TABLE MT_PART_SUPPLIER (
        ID            INT            NOT NULL IDENTITY(1,1),
        PART_ID       NVARCHAR(20)   NOT NULL,
        SUP_ID        NVARCHAR(20)   NOT NULL,
        PART_NO_SUP   NVARCHAR(50)   NULL,
        REMARK        NVARCHAR(200)  NULL,

        CONSTRAINT PK_MT_PART_SUPPLIER  PRIMARY KEY (ID),
        CONSTRAINT FK_MPS_PART          FOREIGN KEY (PART_ID)
            REFERENCES MT_PART_ITEM(PART_ID),
        CONSTRAINT UQ_MT_PART_SUP       UNIQUE (PART_ID, SUP_ID)
    );
    PRINT 'Created: MT_PART_SUPPLIER';
END
GO

-- ------------------------------------------------------------
-- MT_STOCK
-- ------------------------------------------------------------
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'MT_STOCK')
BEGIN
    CREATE TABLE MT_STOCK (
        STOCK_ID      INT            NOT NULL IDENTITY(1,1),
        PART_ID       NVARCHAR(20)   NOT NULL,
        LOC_ID        NVARCHAR(10)   NOT NULL,
        QTY_ON_HAND   DECIMAL(12,2)  NOT NULL DEFAULT 0,
        LAST_UPDATED  DATETIME       NOT NULL DEFAULT GETDATE(),

        CONSTRAINT PK_MT_STOCK       PRIMARY KEY (STOCK_ID),
        CONSTRAINT FK_STK_PART       FOREIGN KEY (PART_ID)
            REFERENCES MT_PART_ITEM(PART_ID),
        CONSTRAINT FK_STK_LOC        FOREIGN KEY (LOC_ID)
            REFERENCES MT_LOCATION(LOC_ID),
        CONSTRAINT UQ_MT_STOCK_PART  UNIQUE (PART_ID)   -- 1 Part : 1 Location
    );
    PRINT 'Created: MT_STOCK';
END
GO

-- ------------------------------------------------------------
-- MT_TXN
-- ------------------------------------------------------------
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'MT_TXN')
BEGIN
    CREATE TABLE MT_TXN (
        TXN_ID        INT            NOT NULL IDENTITY(1,1),
        TXN_TYPE      NVARCHAR(3)    NOT NULL,
        PART_ID       NVARCHAR(20)   NOT NULL,
        LOC_ID        NVARCHAR(10)   NOT NULL,
        QTY           DECIMAL(12,2)  NOT NULL,
        TXN_DATE      DATE           NOT NULL,
        REMARK        NVARCHAR(500)  NULL,
        CREATED_BY    NVARCHAR(50)   NOT NULL,
        CREATED_AT    DATETIME       NOT NULL DEFAULT GETDATE(),
        DEVICE_INFO   NVARCHAR(200)  NULL,

        CONSTRAINT PK_MT_TXN         PRIMARY KEY (TXN_ID),
        CONSTRAINT FK_TXN_PART       FOREIGN KEY (PART_ID)
            REFERENCES MT_PART_ITEM(PART_ID),
        CONSTRAINT FK_TXN_LOC        FOREIGN KEY (LOC_ID)
            REFERENCES MT_LOCATION(LOC_ID),
        CONSTRAINT CK_MT_TXN_TYPE    CHECK (TXN_TYPE IN ('IN','OUT','RET')),
        CONSTRAINT CK_MT_TXN_QTY     CHECK (QTY > 0)
    );
    PRINT 'Created: MT_TXN';
END
GO

-- ------------------------------------------------------------
-- MT_AUDIT_LOG
-- ------------------------------------------------------------
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'MT_AUDIT_LOG')
BEGIN
    CREATE TABLE MT_AUDIT_LOG (
        LOG_ID        INT            NOT NULL IDENTITY(1,1),
        TXN_ID        INT            NOT NULL,
        ACTION        NVARCHAR(20)   NOT NULL,
        CHANGED_BY    NVARCHAR(50)   NOT NULL,
        CHANGED_AT    DATETIME       NOT NULL DEFAULT GETDATE(),
        OLD_VALUE     NVARCHAR(MAX)  NULL,
        NEW_VALUE     NVARCHAR(MAX)  NULL,
        DEVICE_INFO   NVARCHAR(200)  NULL,

        CONSTRAINT PK_MT_AUDIT_LOG   PRIMARY KEY (LOG_ID),
        CONSTRAINT FK_AUDIT_TXN      FOREIGN KEY (TXN_ID)
            REFERENCES MT_TXN(TXN_ID),
        CONSTRAINT CK_MT_ACTION      CHECK (ACTION IN ('CREATE','EDIT','CANCEL'))
    );
    PRINT 'Created: MT_AUDIT_LOG';
END
GO

-- ------------------------------------------------------------
-- Indexes
-- ------------------------------------------------------------
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_MT_TXN_PART')
    CREATE INDEX IX_MT_TXN_PART    ON MT_TXN(PART_ID);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_MT_TXN_DATE')
    CREATE INDEX IX_MT_TXN_DATE    ON MT_TXN(TXN_DATE DESC);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_MT_TXN_USER')
    CREATE INDEX IX_MT_TXN_USER    ON MT_TXN(CREATED_BY);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_MT_AUDIT_TXN')
    CREATE INDEX IX_MT_AUDIT_TXN   ON MT_AUDIT_LOG(TXN_ID);
GO

PRINT '=== MT01 All Tables Created Successfully ===';
GO