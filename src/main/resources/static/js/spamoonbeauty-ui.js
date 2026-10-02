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
})();
