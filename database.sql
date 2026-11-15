-- Варіант 1. Бабенко Дмитро Володимирович, 204-ТН.
-- MySQL 8.4+. Create schema and demonstration rows; no passwords or MySQL users.
-- Existing tables/data are preserved. Run against a new database for the initial demo.
CREATE DATABASE IF NOT EXISTS it_support CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE it_support;

CREATE TABLE IF NOT EXISTS app_user (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 full_name VARCHAR(120) NOT NULL,
 email VARCHAR(190) NOT NULL UNIQUE,
 department VARCHAR(120) NOT NULL,
 technician BOOLEAN NOT NULL DEFAULT FALSE
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS category (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 name VARCHAR(100) NOT NULL UNIQUE,
 description VARCHAR(1000) NOT NULL DEFAULT ''
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS equipment (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 inventory_number VARCHAR(80) NOT NULL UNIQUE,
 name VARCHAR(200) NOT NULL,
 location VARCHAR(200) NOT NULL,
 criticality DECIMAL(5,2) NOT NULL,
 CONSTRAINT ck_equipment_criticality CHECK (criticality BETWEEN 0 AND 100)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS ticket (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 title VARCHAR(200) NOT NULL,
 description VARCHAR(4000) NOT NULL,
 requester_id BIGINT NOT NULL,
 assignee_id BIGINT NULL,
 equipment_id BIGINT NOT NULL,
 category_id BIGINT NOT NULL,
 state VARCHAR(20) NOT NULL DEFAULT 'NEW',
 urgency DECIMAL(5,2) NOT NULL,
 impact DECIMAL(5,2) NOT NULL,
 criticality DECIMAL(5,2) NOT NULL,
 priority DECIMAL(5,2) NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT fk_ticket_requester FOREIGN KEY(requester_id) REFERENCES app_user(id) ON DELETE RESTRICT,
 CONSTRAINT fk_ticket_assignee FOREIGN KEY(assignee_id) REFERENCES app_user(id) ON DELETE RESTRICT,
 CONSTRAINT fk_ticket_equipment FOREIGN KEY(equipment_id) REFERENCES equipment(id) ON DELETE RESTRICT,
 CONSTRAINT fk_ticket_category FOREIGN KEY(category_id) REFERENCES category(id) ON DELETE RESTRICT,
 CONSTRAINT ck_ticket_state CHECK (state IN ('NEW','ASSIGNED','IN_PROGRESS','CLOSED')),
 CONSTRAINT ck_ticket_assignee CHECK (state = 'NEW' OR assignee_id IS NOT NULL),
 CONSTRAINT ck_ticket_urgency CHECK (urgency BETWEEN 0 AND 100),
 CONSTRAINT ck_ticket_impact CHECK (impact BETWEEN 0 AND 100),
 CONSTRAINT ck_ticket_criticality CHECK (criticality BETWEEN 0 AND 100),
 CONSTRAINT ck_ticket_priority CHECK (priority BETWEEN 0 AND 100),
 INDEX ix_ticket_state_priority (state, priority),
 INDEX ix_ticket_created_at (created_at)
) ENGINE=InnoDB;

-- INSERT IGNORE makes re-running the seed non-destructive for existing IDs.
INSERT IGNORE INTO app_user(id,full_name,email,department,technician) VALUES
(1,'Бабенко Дмитро Володимирович','babenko@example.edu','Навчальна лабораторія',FALSE),
(2,'Коваль Олена Андріївна','koval@example.edu','Відділ ІТ-підтримки',TRUE),
(3,'Мельник Андрій Сергійович','melnyk@example.edu','Відділ ІТ-підтримки',TRUE);
INSERT IGNORE INTO category(id,name,description) VALUES
(1,'Мережа','Проблеми доступу до локальної мережі та Інтернету'),
(2,'Програмне забезпечення','Встановлення, налаштування та помилки програм'),
(3,'Обладнання','Діагностика та ремонт робочих пристроїв');
INSERT IGNORE INTO equipment(id,inventory_number,name,location,criticality) VALUES
(1,'PC-001','Робоча станція Lenovo','Аудиторія 201',50),
(2,'NET-001','Комутатор навчального корпусу','Серверна кімната',100),
(3,'PRN-001','Принтер HP LaserJet','Кабінет 204',25);
INSERT IGNORE INTO ticket(id,title,description,requester_id,assignee_id,equipment_id,category_id,state,urgency,impact,criticality,priority) VALUES
(1,'Не запускається IDE','Після оновлення не відкривається середовище розроблення.',1,NULL,1,2,'NEW',50,50,50,50),
(2,'Відсутній доступ до мережі','У навчальному корпусі немає підключення до мережі.',1,2,2,1,'ASSIGNED',100,100,100,100),
(3,'Принтер не друкує','Завдання друку залишаються у черзі.',1,3,3,3,'IN_PROGRESS',25,50,25,33.75),
(4,'Налаштування Maven','Відновлено налаштування Maven на робочій станції.',1,2,1,2,'CLOSED',25,25,50,31.25);
