const DEBUG_MODE = false;
function debugLog(...args) { if (DEBUG_MODE) console.log(...args); }

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
            })
            .catch(err => console.error("Ошибка загрузки аналитики:", err));
    }

    if (analyticsData) {
        renderPieChart(analyticsData.pieData);
        renderBarChart(analyticsData.periodStats);
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
            window.location.href = "/analytics?" + params.toString();
        });
    }

    const chartTitle = document.getElementById("periodChartTitle");
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