# CoachUP Cross-Repository Integration & Security Final Verification Matrix

**Date**: 2026-08-16  
**Repositories**:
- Mobile: `CoachUP-Android` (`feature/react-native-expo`)
- Web Admin: `coachup` (`main`)
- Backend: `Supabase (auiebboyocmkkxbdahqf)`

---

## 1. Cross-System Architecture & Feature Matrix

| Feature Area | Mobile Read | Mobile Write | Admin Read | Admin Write | Canonical Table / RPC / Function | Realtime Sync | Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Authentication & Profile** | `AuthService.ts`, `userService.ts` | `AuthService.ts`, `ProfileScreen.tsx` | `UsersPage.jsx`, `UserEditPage.jsx` | `create-user` Edge Function | `public.users`, Supabase Auth | User metadata sync | **PASS** |
| **Admin User Provisioning** | N/A | N/A | `SalonMembers.jsx` | `create-user` Edge Function (Secure JWT) | `public.users`, `auth.admin.createUser` | Post-create list refresh | **PASS** |
| **Membership & Plans** | `PaymentsScreen.tsx`, `userService.ts` | N/A | `SalonMembers.jsx`, `SalonMembershipPlans.jsx` | `SalonMembers.jsx` | `user_memberships` (`plan_id`, `is_active`), `membership_plans` | Screen focus / Postgres | **PASS** |
| **Payments Recording** | `PaymentsScreen.tsx` | N/A | `SalonMembers.jsx`, `SalonReports.jsx` | `SalonMembers.jsx` | `membership_payments` (`user_id`, `amount`, `payment_method`) | Screen focus | **PASS** |
| **QR Entry & Exit** | `QRScreen.tsx`, `qrService.ts` | `qr-validate` & `qr-exit` Edge Functions | `SalonEntries.jsx` | N/A | `qr_entries` (`entry_date`, `entry_time`, `exit_time`) | Manual / Live entries | **PASS** |
| **Notifications** | `NotificationsScreen.tsx` | `notificationService.ts` (`is_read`, `DELETE`) | `SalonNotifications.jsx` | `SalonNotifications.jsx` (Fan-out inserts) | `notifications` (`message`, `type`, `is_read`, `user_id`) | Supabase Postgres Changes Realtime (`user_id=eq.<uid>`) | **PASS** |
| **Appointments** | `AppointmentsScreen.tsx` | `AppointmentsScreen.tsx` (`insert` status='pending') | `SalonAppointments.jsx` | `SalonAppointments.jsx` (Update status) | `appointments` (`appointment_date`, `start_time`, `end_time`, `service_type`, `status`) | Realtime Postgres & Screen Focus | **PASS** |
| **Area Reservations** | `ReservationsScreen.tsx` | `ReservationsScreen.tsx` (`insert` status='pending') | `SalonReservations.jsx` | `SalonReservations.jsx` (Approve/Reject) | `gym_areas`, `user_reservations` (`status`) | Realtime Postgres & Screen Focus | **PASS** |
| **Assigned Training Programs**| `TrainingScreen.tsx`, `trainingService.ts` | `TrainingScreen.tsx` (Save workout logs) | `SalonAssignedPrograms.jsx` | `SalonAssignedPrograms.jsx` (Assign program) | `user_assigned_programs`, `training_programs` | Priority deduplicated view | **PASS** |
| **Group Classes Booking** | `GroupClassesScreen.tsx` | `groupClassService.ts` (Atomic RPC) | `SalonGroupClasses.jsx` | `SalonGroupClasses.jsx` | `atomic_book_group_class`, `atomic_cancel_group_class`, `class_bookings` | Atomic Capacity & Waiting List | **PASS** |
| **Gym Events & Participation**| `CalendarScreen.tsx` | `gymEventService.ts` (Atomic RPC) | `SalonReports.jsx`, `GymDetailPage.jsx`| `GymDetailPage.jsx` | `atomic_join_event`, `atomic_leave_event`, `event_participants` | Atomic FIFO Promotion | **PASS** |
| **Surveys & Feedback** | `SurveysScreen.tsx`, `surveyService.ts` | `surveyService.ts` (Submit answers JSONB) | `SalonSurveys.jsx` | `SalonSurveys.jsx` (Create questions JSONB) | `surveys` (`questions` JSONB), `survey_responses` (`answers` JSONB) | Screen focus & Admin results | **PASS** |
| **Nutrition Plans & Foods** | `NutritionScreen.tsx`, `nutritionService.ts` | N/A | `SalonNutritionPrograms.jsx` | `SalonNutritionPrograms.jsx` | `user_nutrition_plans`, `nutrition_plans`, `nutrition_meals`, `nutrition_foods` | Active plan status filter | **PASS** |
| **Progress Photos & Tracking**| `ProgressTrackingScreen.tsx` | `ProgressTrackingScreen.tsx` (Storage & DB) | `SalonMemberDetail.jsx` | N/A | `body_measurements`, `progress_photos`, Supabase Storage | Dynamic bucket tracking | **PASS** |
| **Coach Messaging (Direct)** | `CoachChatScreen.tsx` | `CoachChatScreen.tsx` (`sender_type='user'`) | `SalonCoaches.jsx` | `SalonCoaches.jsx` (`sender_type='coach'`) | `coach_messages` (`sender_type`, `is_read`) | Postgres Realtime Changes | **PASS** |
| **Community Feed & Images** | `CommunityScreen.tsx`, `communityService.ts` | `communityService.ts` (Server URL only) | N/A | N/A | `community_posts`, `community_comments`, `community-images` | Safe tenant scoping preserved | **PASS** |
| **Transactional Email Deliverability** | Mobile Auth Flows | Password Reset / Verify Email | Web Login | N/A | Supabase Auth Mailer | Custom SMTP Provider required in Dashboard | **EXTERNAL CONFIG REQUIRED** |

---

## 2. Security Hardening Summary

1. **Row Level Security (RLS)**:
   - Replaced permissive `FOR ALL USING (true)` policies with strict tenant isolation checking `auth.uid()`, `is_manager_for_gym()`, and `is_super_admin()`.
   - Normal users can only view and modify their own data; gym managers can only manage data within their assigned `managed_gym_id`.
2. **Edge Function Authorization**:
   - `create-user`: Requires valid Bearer JWT. Validates caller profile and ensures gym managers cannot create super-admins or add members to foreign gyms. Automatically generates secure 16-character passwords server-side.
   - `qr-validate` & `qr-exit`: Enforces `Authorization: Bearer <JWT>` and binds execution strictly to `auth.uid()`.
3. **Atomic RPCs**:
   - `atomic_book_group_class`, `atomic_cancel_group_class`, `atomic_join_event`, `atomic_leave_event` now execute with `SET search_path = public`, validate `auth.uid()` against caller identity, and lock capacity rows (`FOR UPDATE`) to prevent race conditions.
4. **No Fake Success / No Silent Fallback**:
   - Removed AsyncStorage fake persistence on remote write failures across appointments, reservations, surveys, events, group classes, community images, and progress photos. Real errors are bubbled to the user UI.
