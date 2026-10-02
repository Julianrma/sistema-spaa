(function () {
    'use strict';

    const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    const revealItems = document.querySelectorAll('[data-reveal]');
    const navbars = document.querySelectorAll('.navbar');

    const reveal = (element) => element.classList.add('is-visible');

    if (reduceMotion || !('IntersectionObserver' in window)) {
        revealItems.forEach(reveal);
    } else {
        const observer = new IntersectionObserver((entries, currentObserver) => {
            entries.forEach((entry) => {
                if (entry.isIntersecting) {
                    reveal(entry.target);
                    currentObserver.unobserve(entry.target);
                }
            });
        }, { threshold: 0.12, rootMargin: '0px 0px -40px' });

        revealItems.forEach((element) => observer.observe(element));
    }

    const updateNavigation = () => {
        navbars.forEach((navbar) => navbar.classList.toggle('is-scrolled', window.scrollY > 18));
    };

    updateNavigation();
    window.addEventListener('scroll', updateNavigation, { passive: true });

    document.querySelectorAll('.navbar-toggler').forEach((toggle) => {
        const target = document.getElementById(toggle.getAttribute('aria-controls'));
        if (!target) return;
        toggle.addEventListener('click', () => {
            const open = target.classList.toggle('show');
            toggle.setAttribute('aria-expanded', String(open));
        });
    });

    const modal = document.getElementById('serviceModal');
    if (modal) {
        const dialog = modal.querySelector('.service-modal-dialog');
        const image = document.getElementById('serviceModalImage');
        const title = document.getElementById('serviceModalTitle');
        const category = document.getElementById('serviceModalCategory');
        const description = document.getElementById('serviceModalDescription');
        const duration = document.getElementById('serviceModalDuration');
        const price = document.getElementById('serviceModalPrice');
        const booking = document.getElementById('serviceModalBooking');
        let previousFocus = null;

        const closeModal = () => {
            modal.hidden = true;
            document.body.classList.remove('service-modal-open');
            previousFocus?.focus();
        };

        const openModal = (trigger) => {
            previousFocus = trigger.classList.contains('service-modal-data') ? null : trigger;
            title.textContent = trigger.dataset.serviceName || 'Servicio SpaMoonBeauty';
            category.textContent = trigger.dataset.serviceCategory || 'Experiencia de bienestar';
            description.textContent = trigger.dataset.serviceDescription || 'Consulta los detalles de este servicio.';
            duration.textContent = trigger.dataset.serviceDuration || '';
            price.textContent = trigger.dataset.servicePrice || '';
            booking.href = '/agendar?servicioId=' + encodeURIComponent(trigger.dataset.serviceId);
            image.alt = trigger.dataset.serviceName || '';
            if (trigger.dataset.serviceImage) {
                image.classList.remove('is-broken');
                image.src = trigger.dataset.serviceImage;
            } else {
                image.classList.add('is-broken');
                image.removeAttribute('src');
            }
            modal.hidden = false;
            document.body.classList.add('service-modal-open');
            dialog.focus();
        };

        document.querySelectorAll('[data-service-modal-trigger]').forEach((trigger) => {
            trigger.addEventListener('click', () => openModal(trigger));
        });
        modal.querySelectorAll('[data-service-modal-close]').forEach((element) => element.addEventListener('click', closeModal));
        modal.addEventListener('keydown', (event) => {
            if (event.key === 'Escape') {
                event.preventDefault();
                closeModal();
            }
            if (event.key === 'Tab') {
                const focusable = [...modal.querySelectorAll('button, a[href]')].filter((element) => !element.disabled);
                const first = focusable[0];
                const last = focusable[focusable.length - 1];
                if (event.shiftKey && document.activeElement === first) {
                    event.preventDefault();
                    last.focus();
                } else if (!event.shiftKey && document.activeElement === last) {
                    event.preventDefault();
                    first.focus();
                }
            }
        });

        const selectedServiceId = new URLSearchParams(window.location.search).get('servicioId');
        if (selectedServiceId) {
            const trigger = document.querySelector(`[data-service-id="${CSS.escape(selectedServiceId)}"]`);
            if (trigger) openModal(trigger);
        }
    }

    document.querySelectorAll('[data-service-image]').forEach((image) => {
        const markBroken = () => {
            image.classList.add('is-broken');
            image.removeAttribute('src');
        };
        image.addEventListener('error', markBroken);
        if (image.complete && image.naturalWidth === 0) markBroken();
    });
})();
