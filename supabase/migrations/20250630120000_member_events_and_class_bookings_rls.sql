-- Üyeler kendi takvim etkinliklerini ve grup dersi kayıtlarını yönetebilsin.

ALTER TABLE user_events ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "users_manage_own_events" ON user_events;
CREATE POLICY "users_manage_own_events"
    ON user_events
    FOR ALL
    TO authenticated
    USING (user_id = auth.uid())
    WITH CHECK (user_id = auth.uid());

ALTER TABLE class_bookings ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "users_manage_own_class_bookings" ON class_bookings;
CREATE POLICY "users_manage_own_class_bookings"
    ON class_bookings
    FOR ALL
    TO authenticated
    USING (user_id = auth.uid())
    WITH CHECK (user_id = auth.uid());
