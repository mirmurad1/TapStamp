-- Təsdiq gözləyən kampaniya dəyişdirilə bilər: son dəyişiklik vaxtı tarixçədə göstərilir
ALTER TABLE campaigns ADD COLUMN updated_at timestamptz;
