# API Keys Setup Guide

## 📍 Location
**File:** `.env` (in project root directory)

This file has been created for you. Fill in your actual API keys below.

---

## 🔐 Step-by-Step Setup

### **1. Gmail SMTP (For Email Notifications)**

1. Go to your **Google Account**: https://myaccount.google.com
2. Enable **2-Step Verification** (Security → 2-Step Verification)
3. Go to **App Passwords**: https://myaccount.google.com/apppasswords
4. Select **Mail** and your device
5. Copy the **16-character password**
6. Update in `.env`:
   ```
   ADVISORA_SMTP_USER=youremail@gmail.com
   ADVISORA_SMTP_PASSWORD=abcd efgh ijkl mnop  # (remove spaces)
   ```

### **2. Stripe Payment API (For Event Payments)**

1. Go to **Stripe Dashboard**: https://dashboard.stripe.com/register
2. Create account or log in
3. Switch to **Test Mode** (toggle in top right)
4. Go to **Developers → API Keys**: https://dashboard.stripe.com/test/apikeys
5. Copy your keys:
   - **Secret key** (starts with `sk_test_`)
   - **Publishable key** (starts with `pk_test_`)
6. Update in `.env`:
   ```
   STRIPE_SECRET_KEY=sk_test_51Abc...
   STRIPE_PUBLISHABLE_KEY=pk_test_51Xyz...
   ```

---

## ✅ Test Your Setup

### Test SMTP:
```bash
# Check if environment variables are loaded
# Look for no errors when app starts
```

### Test Stripe:
1. Use test card: `4242 4242 4242 4242`
2. Any future expiry date (e.g., 12/28)
3. Any 3-digit CVC

---

## 🔒 Security Notes

✅ The `.env` file is **already in `.gitignore`** - your keys won't be committed  
✅ Never share your `.env` file  
✅ Use **test keys** for development  
✅ Use **production keys** only in production environment

---

## 🚀 After Configuration

1. Save the `.env` file
2. Run database migrations:
   - `event_professional_phase1.sql`
   - `event_professional_phase2.sql`
   - `event_professional_phase3.sql`
3. Start your application

---

## 📧 Email Templates Location
`src/main/resources/`
- `booking-pending-payment.txt`
- `booking-confirmed.txt`
- `booking-refunded.txt`
- `event-reminder.txt`

**Note:** QR ticket images are attached automatically to confirmation emails.
