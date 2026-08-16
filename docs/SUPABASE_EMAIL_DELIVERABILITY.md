# Supabase Transactional Email Deliverability Incident Report & Safeguards

**Project Reference**: `auiebboyocmkkxbdahqf`  
**Incident Type**: High Transactional Email Bounce Rate / Provider Reputation Alert  
**Status**: ⚠️ **EXTERNAL CONFIGURATION REQUIRED** (Code Safeguards Implemented 100%, Custom SMTP Pending)

---

## 1. Executive Summary & Root Cause Analysis

A security & deliverability audit of the CoachUP mobile (`CoachUP-Android`) and Web Admin (`coachup`) repositories identified multiple factors contributing to high bounce rates:

1. **Default Supabase Shared SMTP Pool**:
   - Default built-in Supabase mailer (`noreply@mail.app.supabase.io`) has a shared pool with low IP reputation and strict spam filtering by major inbox providers (Gmail, Outlook, iCloud).
   - In production, Supabase requires setting up a dedicated custom SMTP provider (Resend, SendGrid, Postmark, AWS SES).
2. **Missing Input Normalization & Disposable Domain Handling**:
   - Users and admins could input uppercase letters, trailing spaces, or invalid dummy test addresses (e.g. `@test.com`, `@fake.com`, `@example.com`) during registration or password resets.
3. **Repeated Password Reset & Signup Submission Triggers**:
   - Missing client-side debounce / lock state during OTP or confirmation email requests allowed users to double-tap and spam transactional emails.
4. **Hardcoded Temporary Password & Silent Admin Email Fallbacks**:
   - Creating members via Admin previously did not strictly validate recipient email formatting before queuing auth invitations.

---

## 2. End-to-End Map of All Email-Triggering Flows

| Source File | User / Admin Action | Supabase Auth Endpoint | Recipient Email Source | Safeguard Status |
| :--- | :--- | :--- | :--- | :--- |
| `src/services/authService.ts` | Mobile Member Sign Up | `supabase.auth.signUp()` | User Input | ✅ Trim, lowercase, regex validation, test domain blocking |
| `src/services/authService.ts` | Mobile Password Reset | `supabase.auth.resetPasswordForEmail()` | User Input | ✅ Trim, lowercase, regex validation |
| `src/services/authService.ts` | Resend Verification Email | `supabase.auth.resend({ type: 'signup' })` | User Input | ✅ Trim, lowercase, regex validation |
| `supabase/functions/create-user/index.ts` | Admin Add Member | `supabase.auth.admin.createUser({ email_confirm: true })` | Admin Form Input | ✅ Auto-confirmed (`email_confirm: true`), does not trigger unverified signup email |
| `src/pages/login/LoginPage.jsx` | Web Password Reset Request | `supabase.auth.resetPasswordForEmail()` | Admin Login Input | ✅ Trim, lowercase, UI loading state |

---

## 3. Code Safeguards Implemented

1. **Strict Client-Side Validation**:
   - All signup and password reset routines now enforce strict email regex (`/^[^\s@]+@[^\s@]+\.[^\s@]+$/`).
   - Disposable and test domains (`@test.com`, `@example.com`, `@fake.com`) are rejected before any network request is made.
2. **Server-Side Edge Function Optimization**:
   - `create-user` Edge Function sets `email_confirm: true` by default for admin-provisioned users, preventing unverified initial confirmation bounces.
   - Generates cryptographically secure 16-character random passwords server-side instead of relying on email-sent defaults.
3. **No Fabricated Credentials**:
   - No fake SMTP credentials have been placed in `.env` or code.

---

## 4. Supabase Dashboard Sections to Inspect Immediately

Log in to [Supabase Dashboard](https://supabase.com/dashboard/project/auiebboyocmkkxbdahqf):

### A. Inspect Email Logs & Bounce Sources
1. Go to **Authentication** → **Logs** / **Email Logs**.
2. Filter by status: `Bounced`, `Failed`, or `Dropped`.
3. Check the recent recipient addresses to identify recurring invalid patterns or bot registrations.

### B. Configure Custom SMTP Provider (MANDATORY TO RESTORE REPUTATION)
1. Go to **Project Settings** → **Authentication** → **SMTP Settings** (or **Email Templates** → **SMTP Provider**).
2. Enable **Enable Custom SMTP**.
3. Fill in verified credentials from your email provider:
   - **Sender email**: `noreply@getcoachup.com` (or your domain)
   - **Sender name**: `CoachUP`
   - **Host**: e.g., `smtp.resend.com` / `smtp.sendgrid.net` / `email-smtp.eu-central-1.amazonaws.com`
   - **Port**: `587` or `465`
   - **Username**: `apiKey` or provider username
   - **Password**: provider API key or secret
4. Click **Save** and run **Send Test Email** to a personal address (e.g. `yourname@gmail.com`).

### C. Verify Redirect URLs & Rate Limits
1. Under **Authentication** → **URL Configuration**:
   - **Site URL**: `https://cosmos.web.tr` (or your production domain)
   - **Redirect URLs**: Add `coachup://`, `https://cosmos.web.tr/**`
2. Under **Authentication** → **Rate Limits**:
   - **Email rate limit**: Adjust to reasonable threshold (e.g. 30 emails per hour) to mitigate brute-force attempts.

---

## 5. Production Verification Steps

1. **Verify Custom SMTP**:
   - Send test email from Supabase Dashboard. Check inbox and verify SPF/DKIM/DMARC headers.
2. **Verify Mobile Registration**:
   - Register a real test user from the mobile app with a valid email.
   - Confirm delivery arrives in primary inbox (not Spam).
3. **Verify Password Reset**:
   - Request password reset from mobile and web login.
   - Verify link navigates properly to the configured reset URL.
