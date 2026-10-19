-- Fərdi müraciət: "Hörmətli <ad>," (adı yoxdursa "Hörmətli istifadəçi,"). SMS sayı hər müştərinin adı ilə hesablanır,
-- ona görə segments_per_message — ən çox hissə sayıdır, total_sms isə dəqiq cəmdir.
ALTER TABLE campaigns ADD COLUMN personalized boolean NOT NULL DEFAULT false;
