document.addEventListener('DOMContentLoaded', () => {
  // Mobile Menu Toggle
  const menuBtn = document.getElementById('menuToggle');
  const nav = document.getElementById('navbar');

  if (menuBtn && nav) {
    menuBtn.addEventListener('click', () => {
      nav.classList.toggle('open');
      menuBtn.innerHTML = nav.classList.contains('open') ? 'âœ•' : 'â˜°';
    });
  }

  // Smooth Scrolling for anchor links
  document.querySelectorAll('a[href^="#"]').forEach(anchor => {
    anchor.addEventListener('click', function (e) {
      e.preventDefault();
      const target = document.querySelector(this.getAttribute('href'));
      if (target) {
        target.scrollIntoView({
          behavior: 'smooth'
        });
        if (nav && nav.classList.contains('open')) {
          nav.classList.remove('open');
          menuBtn.innerHTML = 'â˜°';
        }
      }
    });
  });

  // Form Submission & Authentication Logic
  const forms = document.querySelectorAll('form:not(#bookingForm)');
  forms.forEach(form => {
    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      
      const formId = form.getAttribute('id');
      const emailInput = form.querySelector('#email');
      const passwordInput = form.querySelector('#password');
      const btn = form.querySelector('button[type="submit"]');
      const originalText = btn ? btn.innerHTML : 'Submit';

      if (btn) {
        btn.innerHTML = 'Processing...';
        btn.disabled = true;
        btn.style.opacity = '0.7';
      }

      try {
        if (formId === 'registerForm') {
          const roleInput = form.querySelector('#role');
          const fullnameInput = form.querySelector('#fullname');
          const fullName = fullnameInput ? fullnameInput.value : emailInput.value.split('@')[0];
          const role = roleInput ? roleInput.value : 'customer';

          let backendUser = null;
          try {
            const regRes = await fetch('http://localhost:8081/api/users/register', {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({
                fullName: fullName,
                email: emailInput.value,
                password: passwordInput.value,
                role: role === 'admin' ? 'admin' : 'ROLE_CUSTOMER'
              })
            });
            if (regRes.ok) {
              backendUser = await regRes.json();
            }
          } catch (netErr) {
            console.warn('Backend register offline, fallback to local store:', netErr);
          }

          const users = JSON.parse(localStorage.getItem('users') || '[]');
          const existing = users.find(u => u.email.toLowerCase() === emailInput.value.toLowerCase());
          if (existing) {
            alert('Email is already registered!');
            if (btn) { btn.innerHTML = originalText; btn.disabled = false; btn.style.opacity = '1'; }
            return;
          }

          const newUser = {
            id: (backendUser && backendUser.id) ? backendUser.id : Math.floor(1000 + Math.random() * 9000),
            fullname: fullName,
            fullName: fullName,
            email: emailInput.value,
            password: passwordInput.value,
            role: role
          };
          users.push(newUser);
          localStorage.setItem('users', JSON.stringify(users));

          // Auto-upload driving licence if chosen during registration
          const regLicenceInput = form.querySelector('#regLicenceInput');
          if (regLicenceInput && regLicenceInput.files.length > 0 && backendUser && backendUser.id) {
            try {
              const formData = new FormData();
              formData.append('file', regLicenceInput.files[0]);
              await fetch('http://localhost:8081/api/client/licence/upload', {
                method: 'POST',
                headers: { 'X-User-Id': backendUser.id },
                body: formData
              });
            } catch (licErr) {
              console.warn('Licence auto-upload during registration error:', licErr);
            }
          }

          if (btn) {
            btn.innerHTML = 'Account Created!';
            btn.style.backgroundColor = '#10b981';
          }
          setTimeout(() => {
            window.location.href = role === 'admin' ? 'admin-login.html' : 'login.html';
          }, 1000);
          return;
        }

        if (formId === 'customerLoginForm' || formId === 'adminLoginForm') {
          const expectedRole = formId === 'adminLoginForm' ? 'admin' : 'customer';

          // Strictly enforce: only ONE admin (admin@veloce.com) can log in to admin portal
          if (expectedRole === 'admin') {
            if (emailInput.value.trim().toLowerCase() !== 'admin@veloce.com') {
              alert('Access Denied: Only the designated system administrator (admin@veloce.com) is permitted to log in.');
              if (btn) { btn.innerHTML = originalText; btn.disabled = false; btn.style.opacity = '1'; }
              return;
            }
          }

          if (expectedRole === 'customer') {
            if (emailInput.value.trim().toLowerCase() === 'admin@veloce.com') {
              alert('Administrator account detected. Please use the Admin Login portal.');
              if (btn) { btn.innerHTML = originalText; btn.disabled = false; btn.style.opacity = '1'; }
              return;
            }
          }

          let loggedInUser = null;

          // Try backend login first
          try {
            const loginRes = await fetch('http://localhost:8081/api/users/login', {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({
                email: emailInput.value,
                password: passwordInput.value
              })
            });

            if (loginRes.ok) {
              loggedInUser = await loginRes.json();
            }
          } catch (netErr) {
            console.warn('Backend login fallback to local storage:', netErr);
          }

          // Fallback to local storage if backend didn't return user
          if (!loggedInUser) {
            const users = JSON.parse(localStorage.getItem('users') || '[]');
            const localUser = users.find(u => u.email.toLowerCase() === emailInput.value.toLowerCase() && u.password === passwordInput.value);
            if (localUser) {
              loggedInUser = localUser;
            }
          }

          if (!loggedInUser) {
            alert('Invalid email or password! Please check your credentials.');
            if (btn) { btn.innerHTML = originalText; btn.disabled = false; btn.style.opacity = '1'; }
            return;
          }

          // Role verification
          const userRole = (loggedInUser.role || '').toLowerCase();
          if (expectedRole === 'customer' && userRole.includes('admin')) {
            alert('Please use the Admin Login for administrator accounts.');
            if (btn) { btn.innerHTML = originalText; btn.disabled = false; btn.style.opacity = '1'; }
            return;
          }
          if (expectedRole === 'admin' && !userRole.includes('admin')) {
            alert('Unauthorized. You do not have administrator privileges.');
            if (btn) { btn.innerHTML = originalText; btn.disabled = false; btn.style.opacity = '1'; }
            return;
          }

          // Normalize user object
          loggedInUser.fullname = loggedInUser.fullName || loggedInUser.fullname || loggedInUser.email.split('@')[0];
          loggedInUser.role = expectedRole;
          localStorage.setItem('currentUser', JSON.stringify(loggedInUser));

          // Check if file was selected on login screen
          const loginLicenseUpload = form.querySelector('#loginLicenseUpload');
          if (loginLicenseUpload && loginLicenseUpload.files.length > 0) {
            try {
              const formData = new FormData();
              formData.append('file', loginLicenseUpload.files[0]);
              await fetch(`http://localhost:8081/api/client/licence/upload`, {
                method: 'POST',
                headers: { 'X-User-Id': loggedInUser.id || 1 },
                body: formData
              });
            } catch (licErr) {
              console.warn('Could not auto-upload licence during login:', licErr);
            }
          }

          if (btn) {
            btn.innerHTML = 'Success!';
            btn.style.backgroundColor = '#10b981';
          }
          setTimeout(() => {
            window.location.href = expectedRole === 'admin' ? 'admin-dashboard.html' : 'index.html';
          }, 1000);
          return;
        }

        // Other forms (e.g. general action forms)
        const action = form.getAttribute('action');
        if (action) {
          window.location.href = action;
        } else {
          form.reset();
          if (btn) {
            btn.innerHTML = originalText;
            btn.disabled = false;
            btn.style.opacity = '1';
          }
        }
      } catch (err) {
        console.error('Form submission error:', err);
        alert('An error occurred during submission: ' + err.message);
        if (btn) { btn.innerHTML = originalText; btn.disabled = false; btn.style.opacity = '1'; }
      }
    });
  });

  // Display Logged In User
  const currentUser = JSON.parse(localStorage.getItem('currentUser'));
  if (currentUser) {
    const nav = document.getElementById('navbar');
    if (nav) {
      const loginLink = Array.from(nav.querySelectorAll('a')).find(a => a.textContent.includes('Customer Login') || a.textContent.includes('Sign In'));
      const registerLink = Array.from(nav.querySelectorAll('a')).find(a => a.textContent.includes('Register'));
      const adminLink = Array.from(nav.querySelectorAll('a')).find(a => a.textContent.includes('Admin Login'));

      if (currentUser.role === 'customer') {
        if (loginLink) {
          loginLink.textContent = `Welcome, ${currentUser.fullname || currentUser.email.split('@')[0]}`;
          loginLink.href = "customer-bookings.html";
          loginLink.style.color = "#d4af37";
          loginLink.style.fontWeight = "bold";
        }
        if (adminLink) adminLink.style.display = 'none';
      } else if (currentUser.role === 'admin') {
        if (loginLink) {
          loginLink.textContent = `Admin: ${currentUser.fullname || currentUser.email.split('@')[0]}`;
          loginLink.href = "admin-dashboard.html";
          loginLink.style.color = "#ef4444";
        }
        if (adminLink) adminLink.style.display = 'none';
      }

      if (registerLink) {
        registerLink.textContent = 'Logout';
        registerLink.href = "#";
        registerLink.style.backgroundColor = "transparent";
        registerLink.style.border = "1px solid var(--border-color)";
        registerLink.addEventListener('click', (e) => {
          e.preventDefault();
          localStorage.removeItem('currentUser');
          window.location.href = 'index.html';
        });
      }
    }
  }

  // Active link highlighting
  const currentLocation = window.location.pathname;
  const navLinks = document.querySelectorAll('nav a');
  
  navLinks.forEach(link => {
    // Basic matching for demo purposes
    if (link.getAttribute('href') !== '#' && currentLocation.includes(link.getAttribute('href').replace('.html', ''))) {
      link.classList.add('active');
    }
  });

  // Password Visibility Toggle
  const togglePasswordBtns = document.querySelectorAll('.toggle-password');
  togglePasswordBtns.forEach(btn => {
    btn.addEventListener('click', function(e) {
      e.preventDefault();
      const input = this.previousElementSibling;
      if (input && input.tagName === 'INPUT') {
        const currentType = input.getAttribute('type');
        const newType = currentType === 'password' ? 'text' : 'password';
        input.setAttribute('type', newType);
        this.textContent = newType === 'password' ? 'ðŸ‘ï¸' : 'ðŸ”’';
      }
    });
  });

  // Registration Licence File Selection & Preview Handler
  const regLicenceInput = document.getElementById('regLicenceInput');
  if (regLicenceInput) {
    regLicenceInput.addEventListener('change', (e) => {
      const file = e.target.files[0];
      const nameSpan = document.getElementById('regSelectedFileName');
      const previewArea = document.getElementById('regPreviewContainer');
      const imgBox = document.getElementById('regImagePreviewBox');
      const previewImg = document.getElementById('regPreviewImg');
      const pdfBox = document.getElementById('regPdfPreviewBox');
      const pdfName = document.getElementById('regPdfNameDisplay');

      if (!file) {
        if (nameSpan) nameSpan.textContent = 'No file chosen';
        if (previewArea) previewArea.style.display = 'none';
        return;
      }

      if (file.size > 5 * 1024 * 1024) {
        alert('File size exceeds 5 MB limit. Please select a smaller file.');
        regLicenceInput.value = '';
        if (nameSpan) nameSpan.textContent = 'No file chosen';
        if (previewArea) previewArea.style.display = 'none';
        return;
      }

      const validExts = ['.jpg', '.jpeg', '.png', '.pdf'];
      const fileName = file.name.toLowerCase();
      const isValid = validExts.some(ext => fileName.endsWith(ext));
      if (!isValid) {
        alert('Invalid file format. Please upload JPG, JPEG, PNG, or PDF.');
        regLicenceInput.value = '';
        if (nameSpan) nameSpan.textContent = 'No file chosen';
        if (previewArea) previewArea.style.display = 'none';
        return;
      }

      if (nameSpan) nameSpan.textContent = `Selected: ${file.name} (${(file.size / 1024).toFixed(1)} KB)`;
      if (previewArea) previewArea.style.display = 'block';

      if (file.type.includes('image')) {
        if (pdfBox) pdfBox.style.display = 'none';
        if (imgBox) imgBox.style.display = 'block';
        const reader = new FileReader();
        reader.onload = (evt) => {
          if (previewImg) previewImg.src = evt.target.result;
        };
        reader.readAsDataURL(file);
      } else {
        if (imgBox) imgBox.style.display = 'none';
        if (pdfBox) pdfBox.style.display = 'block';
        if (pdfName) pdfName.textContent = file.name;
      }
    });
  }
});

// Function to handle payment completion
function completePayment(bookingId) {
  const paymentSelect = document.getElementById(`payment-${bookingId}`);
  const selectedMethod = paymentSelect.value;

  if (!selectedMethod) {
    alert('Please select a payment method');
    return;
  }

  const allBookings = JSON.parse(localStorage.getItem('allBookings') || '[]');
  const bookingIndex = allBookings.findIndex(b => b.id == bookingId);

  if (bookingIndex !== -1) {
    allBookings[bookingIndex].paymentMethod = selectedMethod;
    allBookings[bookingIndex].status = 'PAID';
    localStorage.setItem('allBookings', JSON.stringify(allBookings));

    alert(`Payment successful via ${selectedMethod.replace('_', ' ').toUpperCase()}!\nYour booking is confirmed. Please arrive with your driving license.`);
    location.reload();
  }
}

