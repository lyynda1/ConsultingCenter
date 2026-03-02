# 📧 Email Troubleshooting Guide

## ✅ What Was Fixed

### 1. **SMTP Host Configuration** (.env)
```diff
- ADVISORA_SMTP_HOST=karamhmidi623@gmail.com  ❌ WRONG
+ ADVISORA_SMTP_HOST=smtp.gmail.com           ✅ CORRECT
```

### 2. **App Password Format** (.env)
```diff
- ADVISORA_SMTP_PASSWORD=lccf goxx dwmx mivc  ❌ Has spaces
+ ADVISORA_SMTP_PASSWORD=lccfgoxxdwmxmivc     ✅ No spaces
```

### 3. **Enhanced Logging**
- Added detailed console logs showing SMTP configuration
- Shows email send attempts with ✓ success or ✗ failure
- Prints full error stack traces for debugging
- Shows if SMTP credentials are missing

---

## 🔍 How to Check if Emails Work

### **1. Restart Your Application**
The `.env` file is read at startup, so you need to restart the app for changes to take effect.

### **2. Watch the Console Output**
When you create a booking, you should see logs like:

```
[EVENT-NOTIF] Building EmailSender - Host: smtp.gmail.com | Port: 587 | User: karamhmidi623@gmail.com
[EMAIL] SMTP Config - Host: smtp.gmail.com | Port: 587 | User: karamhmidi623@gmail.com
[EMAIL] Sending to: client@example.com | Subject: Reservation confirmee
[EMAIL] ✓ Email sent successfully to: client@example.com
```

### **3. Common Error Messages**

#### ❌ "SMTP credentials missing"
```
[EVENT-NOTIF] ✗ SMTP credentials missing! Set ADVISORA_SMTP_USER and ADVISORA_SMTP_PASSWORD in .env
```
**Fix:** Check your .env file has both variables set

#### ❌ "Authentication failed"
```
[EMAIL] ✗ Failed to send email: Authentication failed
```
**Fix:** 
- Verify your Gmail App Password is correct (no spaces)
- Make sure 2-Step Verification is enabled on your Google account
- Generate a new App Password at: https://myaccount.google.com/apppasswords

#### ❌ "Connection refused"
```
[EMAIL] ✗ Failed to send email: Connection refused
```
**Fix:** 
- Check if port 587 is blocked by firewall
- Try port 465 with SSL instead

#### ❌ "No recipient email provided"
```
[EVENT-NOTIF] ✗ Email skipped: No recipient email provided
```
**Fix:** The user account doesn't have an email address in the database

---

## 🧪 Quick Test

### **Test 1: Create a Free Event Booking**
1. Login as CLIENT
2. Create/select an event with price = 0
3. Book it
4. Check console for email logs
5. Check your inbox

### **Test 2: Create a Paid Event Booking**
1. Login as CLIENT
2. Create/select an event with price > 0
3. Book it and complete payment
4. Check console for email logs
5. Check your inbox for confirmation + QR attachment

---

## 🔐 Gmail Setup Checklist

- [ ] **2-Step Verification Enabled**
  - Go to: https://myaccount.google.com/security
  - Enable "2-Step Verification"

- [ ] **App Password Generated**
  - Go to: https://myaccount.google.com/apppasswords
  - Select "Mail" and your device
  - Copy the 16-character password
  - Paste in .env (remove spaces!)

- [ ] **Less Secure Apps** (if using old Gmail)
  - Go to: https://myaccount.google.com/lesssecureapps
  - Turn ON (only if App Password doesn't work)

---

## 📝 Current Configuration

Your `.env` file should look like this:

```env
ADVISORA_SMTP_HOST=smtp.gmail.com
ADVISORA_SMTP_PORT=587
ADVISORA_SMTP_USER=karamhmidi623@gmail.com
ADVISORA_SMTP_PASSWORD=lccfgoxxdwmxmivc
```

---

## 🔄 If Still Not Working

### **1. Test with a simple Java email test**
Add this to your App.java main method temporarily:

```java
EmailSender testSender = new EmailSender(
    "smtp.gmail.com", 
    587, 
    "karamhmidi623@gmail.com", 
    "lccfgoxxdwmxmivc"
);
testSender.send("karamhmidi623@gmail.com", "Test", "This is a test email");
System.out.println("Test email sent!");
```

### **2. Check Gmail App Password is Fresh**
- Old app passwords can expire
- Generate a NEW one at: https://myaccount.google.com/apppasswords
- Delete old passwords
- Update .env with new password

### **3. Try Alternative SMTP Settings**
If port 587 doesn't work, try SSL on port 465:

```env
ADVISORA_SMTP_HOST=smtp.gmail.com
ADVISORA_SMTP_PORT=465
```

And update EmailSender.java `createSession()`:
```java
props.put("mail.smtp.auth", "true");
props.put("mail.smtp.socketFactory.port", "465");
props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
props.put("mail.smtp.port", "465");
```

---

## ✅ Expected Console Output (Working)

When emails work correctly, you'll see:

```
[EVENT-NOTIF] Building EmailSender - Host: smtp.gmail.com | Port: 587 | User: karamhmidi623@gmail.com
[EMAIL] SMTP Config - Host: smtp.gmail.com | Port: 587 | User: karamhmidi623@gmail.com
[EMAIL] Sending to: karamhmidi623@gmail.com | Subject: Reservation confirmee
[EMAIL] ✓ Email sent successfully to: karamhmidi623@gmail.com
```

**Then check your Gmail inbox (and spam folder!).**

---

## 📞 Need More Help?

1. **Share console logs** - Copy the [EMAIL] and [EVENT-NOTIF] log lines
2. **Check spam folder** - Emails might be marked as spam initially
3. **Verify Google Account** - Make sure 2FA is enabled
4. **Test with another email** - Try sending to a different address

