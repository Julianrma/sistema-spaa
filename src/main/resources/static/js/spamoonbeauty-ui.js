(function () {
    'use strict';
    document.documentElement.classList.add('js-enabled');

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
        }, { threshold: 0, rootMargin: '0px' });

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

    document.querySelectorAll('[data-hero-image]').forEach((image) => {
        const markHeroBroken = () => {
            image.classList.add('is-broken');
            image.removeAttribute('src');
        };
        image.addEventListener('error', markHeroBroken);
        if (image.complete && image.naturalWidth === 0) markHeroBroken();
    });

    document.querySelectorAll('[data-catalog-hero-image]').forEach((image) => {
        const markCatalogHeroBroken = () => {
            image.classList.add('is-broken');
            image.removeAttribute('src');
        };
        image.addEventListener('error', markCatalogHeroBroken);
        if (image.complete && image.naturalWidth === 0) markCatalogHeroBroken();
    });

    const branchesPage = document.querySelector('.branches-page');
    if (branchesPage) {
        const mapFrame = branchesPage.querySelector('#mapFrame');
        const selectedBranchName = branchesPage.querySelector('#selectedBranchName');
        const branchCards = [...branchesPage.querySelectorAll('.branch-card')];
        const mapTriggers = branchesPage.querySelectorAll('[data-branch-map-trigger]');

        const showBranchOnMap = (card) => {
            if (!mapFrame || !card) return;
            const { lat, lng, name } = card.dataset;
            mapFrame.src = `https://maps.google.com/maps?q=${encodeURIComponent(lat)},${encodeURIComponent(lng)}&z=15&output=embed`;
            mapFrame.title = `Mapa de ${name}`;
            selectedBranchName.textContent = name;
            branchCards.forEach((item) => item.classList.toggle('active', item === card));
        };

        mapTriggers.forEach((trigger) => {
            trigger.addEventListener('click', () => showBranchOnMap(trigger.closest('.branch-card')));
        });
    }

    const bookingPage = document.querySelector('.booking-page');
    if (bookingPage) {
        const branchSelect = bookingPage.querySelector('#sucursalId');
        const serviceSelect = bookingPage.querySelector('#servicioId');
        const masseuseSelect = bookingPage.querySelector('#masajistaId');
        const dateInput = bookingPage.querySelector('#fecha');
        const timeInput = bookingPage.querySelector('#horaSeleccionada');
        const summary = {
            branch: bookingPage.querySelector('#summaryBranch'),
            service: bookingPage.querySelector('#summaryService'),
            masseuse: bookingPage.querySelector('#summaryMasseuse'),
            date: bookingPage.querySelector('#summaryDate'),
            time: bookingPage.querySelector('#summaryTime'),
            price: bookingPage.querySelector('#summaryPrice')
        };

        const selectedText = (select, fallback) => select?.selectedOptions[0]?.value
            ? select.selectedOptions[0].textContent.trim()
            : fallback;

        const formatDate = (value) => {
            if (!value) return 'Por elegir';
            const parsed = new Date(value + 'T00:00:00');
            return Number.isNaN(parsed.getTime()) ? value : new Intl.DateTimeFormat('es-EC', {
                day: 'numeric', month: 'short', year: 'numeric'
            }).format(parsed);
        };

        const updateBookingSummary = () => {
            if (!branchSelect || !serviceSelect || !masseuseSelect || !dateInput || !timeInput) return;
            const serviceOption = serviceSelect.selectedOptions[0];
            summary.branch.textContent = selectedText(branchSelect, 'Selecciona una sede');
            summary.service.textContent = serviceOption?.dataset.serviceName || selectedText(serviceSelect, 'Por elegir');
            summary.masseuse.textContent = selectedText(masseuseSelect, 'Por elegir');
            summary.date.textContent = formatDate(dateInput.value);
            summary.time.textContent = timeInput.value || 'Por elegir';
            summary.price.textContent = serviceOption?.dataset.price ? '$' + serviceOption.dataset.price : 'Por elegir';
        };

        const refreshAvailability = () => {
            const branch = branchSelect.value;
            const masseuse = masseuseSelect.value;
            const service = serviceSelect.value;
            const date = dateInput.value;
            updateBookingSummary();
            if (branch && masseuse && service && date) {
                window.location.href = '/agendar/disponibilidad?sucursalId=' + encodeURIComponent(branch)
                    + '&masajistaId=' + encodeURIComponent(masseuse)
                    + '&servicioId=' + encodeURIComponent(service)
                    + '&fecha=' + encodeURIComponent(date);
            }
        };

        [branchSelect, masseuseSelect, serviceSelect, dateInput].forEach((field) => {
            field?.addEventListener('change', refreshAvailability);
        });

        timeInput?.addEventListener('change', updateBookingSummary);

        updateBookingSummary();
    }
    document.querySelectorAll('[data-copy-code]').forEach((button) => {
        if (!navigator.clipboard || !window.isSecureContext) return;
        button.hidden = false;
        button.addEventListener('click', async () => {
            const code = document.getElementById(button.dataset.copyCode);
            const feedback = button.parentElement.querySelector('.copy-feedback');
            try {
                await navigator.clipboard.writeText(code.textContent.trim());
                feedback.textContent = 'Código copiado. Guárdalo en un lugar privado.';
            } catch (_) {
                feedback.textContent = 'Selecciona y copia el código manualmente.';
            }
        });
    });
})();

// Hash sections share the dashboard route; expose their active location to keyboard users.
(() => {
    const nav = document.querySelector('.workspace-links');
    if (!nav || !['/admin/dashboard', '/admin/services/edit'].includes(location.pathname)) return;
    const updateSection = () => {
        const hash = location.pathname === '/admin/services/edit' ? '#servicios' : location.hash;
        const destination = hash === '#resenas' ? '/admin/dashboard#resenas' :
            ['#servicios', '#service-form'].includes(hash) ? '/admin/dashboard#servicios' : '/admin/dashboard';
        nav.querySelectorAll('a').forEach(link => {
            if (link.getAttribute('href') === destination) link.setAttribute('aria-current', 'page');
            else link.removeAttribute('aria-current');
        });
    };
    updateSection();
    window.addEventListener('hashchange', updateSection);
})();
