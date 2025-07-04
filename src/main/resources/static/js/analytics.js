// Глобальный режим отладки. Используем глобальную переменную, если она уже
// определена другим скриптом (например, app.js)
window.DEBUG_MODE = window.DEBUG_MODE || false;
function debugLog(...args) { if (window.DEBUG_MODE) console.log(...args); }

document.addEventListener("DOMContentLoaded", function () {
    debugLog("analytics.js loaded!")
    // --- CSRF-токен
    const csrfToken = document.querySelector('meta[name="_csrf"]')?.content || "";
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content || "";

    // --- Переменные
    let pieChart = null;
    let barChart = null;
    let analyticsData = window.analyticsData || null;

    // --- Контексты графиков
    const refreshAnalyticsBtn = document.getElementById("refreshAnalyticsBtn");
    const storeSelect = document.getElementById("analyticsStoreSelect");
    const periodSelect = document.getElementById("periodSelect");
    let selectedPeriod = periodSelect?.value || "WEEKS";

    // Элемент заголовка графика должен быть известен до первого вызова функции
    const chartTitle = document.getElementById("periodChartTitle");
    updateChartTitle(selectedPeriod); // установить заголовок при первой загрузке

    const pieCtx = document.getElementById('statusPieChart')?.getContext('2d');
    const barCtx = document.getElementById('periodBarChart')?.getContext('2d');

    const toggleButtons = document.querySelectorAll(".toggle-store-btn");
    const COLLAPSED_KEY = "collapsedStores";

    // Получить сохранённые ID
    function getCollapsedStores() {
        return JSON.parse(localStorage.getItem(COLLAPSED_KEY)) || [];
    }

    // Сохранить ID
    function saveCollapsedStores(ids) {
        localStorage.setItem(COLLAPSED_KEY, JSON.stringify(ids));
    }

    // Применить сохранённые состояния
    const collapsedIds = getCollapsedStores();
    collapsedIds.forEach(storeId => {
        const content = document.querySelector(`.store-statistics-content[data-store-id="${storeId}"]`);
        const toggleBtn = document.querySelector(`.toggle-store-btn[data-store-id="${storeId}"]`);
        if (content && toggleBtn) {
            content.classList.remove("expanded");
            content.classList.add("collapsed");
            const icon = toggleBtn.querySelector("i");
            icon?.classList.remove("bi-chevron-up");
            icon?.classList.add("bi-chevron-down");
        }
    });

    // Переключение состояния по клику
    toggleButtons.forEach(btn => {
        btn.addEventListener("click", function () {
            const storeId = this.getAttribute("data-store-id");
            const content = document.querySelector(`.store-statistics-content[data-store-id="${storeId}"]`);
            const icon = this.querySelector("i");

            if (!content) return;

            const collapsed = content.classList.toggle("collapsed");
            content.classList.toggle("expanded", !collapsed);

            icon?.classList.toggle("bi-chevron-down", collapsed);
            icon?.classList.toggle("bi-chevron-up", !collapsed);

            // Обновить localStorage
            let current = getCollapsedStores();
            if (collapsed) {
                if (!current.includes(storeId)) current.push(storeId);
            } else {
                current = current.filter(id => id !== storeId);
            }
            saveCollapsedStores(current);
        });
    });

    // --- Функции рендера
    function renderPieChart(data) {
        const placeholder = document.getElementById('pieNoData');
        if (!pieCtx || !data || (data.delivered + data.returned + data.inTransit === 0)) {
            placeholder?.classList.remove('d-none');
            pieChart?.destroy(); // если вдруг был
            return;
        }

        if (!pieCtx || !data) return;
        placeholder?.classList.add('d-none');
        if (pieChart) pieChart.destroy();

        pieChart = new Chart(pieCtx, {
            type: 'doughnut',
            data: {
                labels: ['Доставлено', 'Возвращено', 'В пути'],
                datasets: [{
                    data: [data.delivered, data.returned, data.inTransit],
                    backgroundColor: ['#198754', '#dc3545', '#ffc107']
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                layout: {
                    padding: 10
                },
                plugins: {
                    legend: {
                        position: 'bottom',
                        labels: {
                            padding: 20,
                            boxWidth: 12,
                            font: {
                                size: 12
                            }
                        }
                    },
                    tooltip: {
                        callbacks: {
                            label: function (context) {
                                const label = context.label || '';
                                const value = context.raw || 0;
                                return `${label}: ${value}`;
                            }
                        }
                    }
                }
            }
        });
    }

    function renderBarChart(stats) {
        const placeholder = document.getElementById('barNoData');
        if (!barCtx || !stats || !stats.labels || stats.labels.length === 0) {
            placeholder?.classList.remove('d-none');
            barChart?.destroy();
            return;
        }

        if (!barCtx || !stats) return;
        placeholder?.classList.add('d-none');
        if (barChart) barChart.destroy();

        barChart = new Chart(barCtx, {
            type: 'bar',
            data: {
                labels: stats.labels,
                datasets: [
                    {
                        label: 'Отправлено',
                        data: stats.sent,
                        backgroundColor: '#0d6efd'
                    },
                    {
                        label: 'Доставлено',
                        data: stats.delivered,
                        backgroundColor: '#198754'
                    },
                    {
                        label: 'Возвращено',
                        data: stats.returned,
                        backgroundColor: '#dc3545'
                    }
                ]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                layout: {
                    padding: 10
                },
                scales: {
                    y: {
                        beginAtZero: true,
                        title: {
                            display: true,
                            text: 'Количество посылок',
                            font: {
                                weight: 'bold'
                            }
                        }
                    }
                },
                plugins: {
                    legend: {
                        position: 'bottom',
                        labels: {
                            padding: 20,
                            boxWidth: 12,
                            font: {
                                size: 12
                            }
                        }
                    },
                    tooltip: {
                        callbacks: {
                            label: function (context) {
                                return `${context.dataset.label}: ${context.raw}`;
                            }
                        }
                    }
                }
            }
        });
    }

    // Обновление сводных статистик магазинов
    function updateStoreStats(stats) {
        if (!stats) return;

        // контейнеры с блоками статистики
        const containers = document.querySelectorAll('.store-statistics-content, .row.text-center.mb-4');
        containers.forEach(container => {
            const values = container.querySelectorAll('.col-md-2 .h5, .col-md-2.col-6 .h5');
            if (values.length < 5) return;

            values[0].textContent = stats.totalSent;
            values[1].textContent = stats.totalDelivered;
            values[2].textContent = stats.totalReturned;
            values[3].textContent = Number(stats.averageDeliveryDays).toFixed(1);
            values[4].textContent = Number(stats.averagePickupDays).toFixed(1);

            const total = stats.totalDelivered + stats.totalReturned;
            if (values[5]) {
                const successRate = total > 0 ? (stats.totalDelivered * 100 / total).toFixed(1) + ' %' : '0.0 %';
                values[5].textContent = successRate;
            }
            if (values[6]) {
                const returnRate = total > 0 ? (stats.totalReturned * 100 / total).toFixed(1) + ' %' : '0.0 %';
                values[6].textContent = returnRate;
            }
        });
    }

    // Обновление таблицы почтовых служб
    function updatePostalServiceStats(stats) {
        if (!Array.isArray(stats)) return;
        const rows = document.querySelectorAll('table.table-hover.align-middle tbody tr');
        rows.forEach(row => {
            const service = row.querySelector('td:first-child')?.textContent.trim();
            const data = stats.find(s => s.postalService === service);
            if (!data) return;

            const cells = row.querySelectorAll('td');
            if (cells.length < 6) return;

            // Отправлено
            const sentBar = cells[1].querySelector('.progress-bar');
            if (sentBar) sentBar.textContent = data.sent;

            // Доставлено
            const deliveredBar = cells[2].querySelector('.progress-bar');
            if (deliveredBar) {
                deliveredBar.style.width = data.sent > 0 ? (data.delivered / data.sent * 100) + '%' : '0%';
                const span = deliveredBar.querySelector('span');
                if (span) span.textContent = data.delivered > 0 ? data.delivered : 0;
            }

            // Возвращено
            const returnedBar = cells[3].querySelector('.progress-bar');
            if (returnedBar) {
                returnedBar.style.width = data.sent > 0 ? (data.returned / data.sent * 100) + '%' : '0%';
                const span = returnedBar.querySelector('span');
                if (span) span.textContent = data.returned > 0 ? data.returned : 0;
            }

            // Среднее время доставки
            const deliverySpan = cells[4].querySelector('span');
            if (deliverySpan) deliverySpan.textContent = Number(data.avgDeliveryDays).toFixed(1);

            // Среднее время забора
            const pickupSpan = cells[5].querySelector('span');
            if (pickupSpan) pickupSpan.textContent = Number(data.avgPickupTimeDays).toFixed(1);
        });
    }

    // --- Загрузка аналитики
    function loadAnalyticsData() {
        debugLog("🔥 [Debug] loadAnalyticsData called");
        const storeId = storeSelect?.value;
        const interval = selectedPeriod;

        const params = new URLSearchParams();
        if (storeId && storeId !== 'all') {
            params.append("storeId", storeId);
        }
        params.append("interval", interval);

        debugLog("loadAnalyticsData invoked!")

        fetch("/analytics/json?" + params.toString())
            .then(res => res.json())
            .then(freshData => {
                debugLog("🚀 [Debug] Fetched data:", freshData);
                analyticsData = freshData;
                renderPieChart(analyticsData.pieData);
                debugLog("🏁 [Debug] rendering bar chart");
                renderBarChart(analyticsData.periodStats);
                updateStoreStats(analyticsData.storeStatistics);
                updatePostalServiceStats(analyticsData.postalStats);
            })
            .catch(err => console.error("Ошибка загрузки аналитики:", err));
    }

    if (analyticsData) {
        renderPieChart(analyticsData.pieData);
        renderBarChart(analyticsData.periodStats);
        updateStoreStats(analyticsData.storeStatistics);
        updatePostalServiceStats(analyticsData.postalStats);
    } else {
        // Если данные не были переданы сервером напрямую, загружаем их
        loadAnalyticsData();
    }

    // Кнопка обновления
    if (refreshAnalyticsBtn) {
        refreshAnalyticsBtn.addEventListener("click", function () {
            refreshAnalyticsBtn.disabled = true;
            refreshAnalyticsBtn.innerHTML = '<i class="bi bi-arrow-repeat spin"></i>';

            const formData = new FormData();
            const storeId = storeSelect?.value;
            if (storeId && storeId !== 'all') {
                formData.append("storeId", storeId);
            }

            fetch("/analytics/update", {
                method: "POST",
                headers: { [csrfHeader]: csrfToken },
                body: formData
            })
                .then(res => res.json())
                .then(data => {
                    notifyUser(data.message, "success");
                    loadAnalyticsData();
                })
                .catch(err => {
                    console.error("Ошибка обновления:", err);
                    notifyUser("Ошибка при обновлении аналитики!", "danger");
                })
                .finally(() => {
                    refreshAnalyticsBtn.disabled = false;
                    refreshAnalyticsBtn.innerHTML = '<i class="bi bi-arrow-repeat"></i> Обновить аналитику';
                });
        });
    }

    // Выбор периода
    if (periodSelect) {
        periodSelect.addEventListener("change", function () {
            selectedPeriod = this.value;
            updateChartTitle(selectedPeriod);
            loadAnalyticsData();
        });
    }

    // Смена магазина
    if (storeSelect) {
        storeSelect.addEventListener("change", function () {
            const storeId = storeSelect.value;
            const params = new URLSearchParams();
            if (storeId !== '') params.append("storeId", storeId);
            params.append("interval", selectedPeriod);

            debugLog("Selected storeId =", storeId);
            document.body.classList.add("loading");
            window.location.href = "/app/analytics?" + params.toString();
        });
    }

    function updateChartTitle(period) {
        let title;
        switch (period) {
            case "DAYS":
                title = "Динамика отправлений по дням";
                break;
            case "WEEKS":
                title = "Динамика отправлений по неделям";
                break;
            case "MONTHS":
                title = "Динамика отправлений по месяцам";
                break;
            case "YEARS":
                title = "Динамика отправлений по годам";
                break;
            default:
                title = "Динамика отправлений";
        }
        if (chartTitle) {
            chartTitle.textContent = title;
        }
    }

});