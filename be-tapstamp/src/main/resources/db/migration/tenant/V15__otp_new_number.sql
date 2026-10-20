-- SMS limitləri (qərar #53): şirkətin gündəlik limiti yalnız sistemdə olmayan (yeni) nömrələrə göndərilən giriş
-- kodlarını sayır — SMS hücumu (pumping) təsadüfi nömrələrə gedir, qeydiyyatlı müştərilər limitə düşmür.
ALTER TABLE otp_challenges ADD COLUMN new_number boolean NOT NULL DEFAULT false;
CREATE INDEX otp_challenges_new_number_idx ON otp_challenges (created_at) WHERE new_number;
