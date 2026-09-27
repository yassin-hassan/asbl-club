-- An email can carry a ticket: the code to draw as a QR image (attached as a PNG when the email is sent) and the
-- attachment's file name. The code is cleared with the body once sent: it lets someone in at the door.
ALTER TABLE email_outbox ADD COLUMN qr_code VARCHAR(100);
ALTER TABLE email_outbox ADD COLUMN qr_file_name VARCHAR(100);
