
    document.addEventListener('DOMContentLoaded', function () {
    const btnMonthly = document.getElementById('btn-monthly');
    const btnYearly = document.getElementById('btn-yearly');
    const monthsInput = document.getElementById('monthsInput');
    const subscribeBtn = document.getElementById('subscribeBtn');

    const showMonthly = () => {
    monthsInput.value = 1;
    btnMonthly.classList.replace('btn-outline-secondary', 'btn-primary');
    btnYearly.classList.replace('btn-primary', 'btn-outline-secondary');
    document.querySelectorAll('.price-monthly').forEach(el => el.classList.remove('d-none'));
    document.querySelectorAll('.price-yearly').forEach(el => el.classList.add('d-none'));
    subscribeBtn.textContent = 'Перейти';
};

    const showYearly = () => {
    monthsInput.value = 12;
    btnMonthly.classList.replace('btn-primary', 'btn-outline-secondary');
    btnYearly.classList.replace('btn-outline-secondary', 'btn-primary');
    document.querySelectorAll('.price-monthly').forEach(el => el.classList.add('d-none'));
    document.querySelectorAll('.price-yearly').forEach(el => el.classList.remove('d-none'));
    subscribeBtn.textContent = 'Перейти ';
};

    btnMonthly.addEventListener('click', showMonthly);
    btnYearly.addEventListener('click', showYearly);

    // По умолчанию — месяц
    showMonthly();
});
