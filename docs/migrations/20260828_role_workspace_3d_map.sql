USE smart_smoke;

CREATE TABLE IF NOT EXISTS map_building (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    building_code VARCHAR(32) NOT NULL,
    building_name VARCHAR(100) NOT NULL,
    position_x DECIMAL(8,2) NOT NULL,
    position_z DECIMAL(8,2) NOT NULL,
    width DECIMAL(8,2) NOT NULL,
    depth DECIMAL(8,2) NOT NULL,
    floors INT NOT NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE INDEX uk_map_building_code (building_code),
    CONSTRAINT chk_map_building_floors CHECK (floors > 0),
    CONSTRAINT chk_map_building_enabled CHECK (enabled IN (0, 1))
);

INSERT INTO map_building
    (building_code, building_name, position_x, position_z, width, depth, floors)
VALUES
    ('A1', '1号住宅楼', 16, 18, 18, 14, 6),
    ('A2', '2号住宅楼', 47, 12, 22, 16, 8),
    ('A3', '3号住宅楼', 75, 28, 17, 13, 5)
ON DUPLICATE KEY UPDATE building_name = VALUES(building_name);

CREATE TABLE IF NOT EXISTS device_map_position (
    device_id VARCHAR(64) PRIMARY KEY,
    building_code VARCHAR(32) NOT NULL,
    floor_no INT NOT NULL,
    room_label VARCHAR(64) NOT NULL,
    position_x DECIMAL(8,2) NOT NULL,
    position_z DECIMAL(8,2) NOT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_map_position_building_floor (building_code, floor_no),
    CONSTRAINT fk_map_position_device FOREIGN KEY (device_id) REFERENCES device(device_id),
    CONSTRAINT fk_map_position_building FOREIGN KEY (building_code) REFERENCES map_building(building_code),
    CONSTRAINT chk_map_position_floor CHECK (floor_no > 0),
    CONSTRAINT chk_map_position_coordinates CHECK (position_x >= 0 AND position_z >= 0)
);

INSERT IGNORE INTO device_map_position
    (device_id, building_code, floor_no, room_label, position_x, position_z, updated_at)
SELECT d.device_id,
       CASE MOD(d.id - 1, 3) WHEN 0 THEN 'A1' WHEN 1 THEN 'A2' ELSE 'A3' END,
       1 + MOD(d.id - 1, 5),
       CONCAT(1 + MOD(d.id - 1, 5), '0', 1 + MOD(d.id - 1, 4)),
       3 + MOD(d.id * 3, 10),
       3 + MOD(d.id * 2, 6),
       NOW()
FROM device d
WHERE d.bound = 1;
