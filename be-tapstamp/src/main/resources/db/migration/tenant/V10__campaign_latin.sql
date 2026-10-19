-- Latın hərfləri ilə göndəriş: bütün mesaj (müraciət, ad, mətn) göndəriləndə latına çevrilir (ə→e, ş→s, ...) —
-- SMS 70 yox, 160 simvol tutur.
ALTER TABLE campaigns ADD COLUMN latin boolean NOT NULL DEFAULT false;
