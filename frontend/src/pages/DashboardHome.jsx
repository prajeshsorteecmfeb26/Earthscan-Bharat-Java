import React, { useState, useEffect, useRef } from 'react';
import { Container, Row, Col, Card, Badge, Button, Form, InputGroup, Spinner } from 'react-bootstrap';
import { MapContainer, TileLayer, Marker, Popup, useMap } from 'react-leaflet';
import 'leaflet/dist/leaflet.css';
import L from 'leaflet';
import { CircularProgress, Box } from '@mui/material';
import html2pdf from 'html2pdf.js';
import InsightsFooter from '../components/InsightsFooter';
import { SavedSearchContext } from '../context/SavedSearchContext';
import { useTranslation } from 'react-i18next';

// Fix for default marker icon in react-leaflet
delete L.Icon.Default.prototype._getIconUrl;
L.Icon.Default.mergeOptions({
    iconRetinaUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-icon-2x.png',
    iconUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-icon.png',
    shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-shadow.png',
});

// Weather code descriptions from Open-Meteo WMO codes
const WMO_CODES = {
    0: { label: 'Clear Sky', icon: 'bi-sun-fill', color: 'text-warning' },
    1: { label: 'Mainly Clear', icon: 'bi-sun-fill', color: 'text-warning' },
    2: { label: 'Partly Cloudy', icon: 'bi-cloud-sun-fill', color: 'text-warning' },
    3: { label: 'Overcast', icon: 'bi-clouds-fill', color: 'text-secondary' },
    45: { label: 'Foggy', icon: 'bi-cloud-fog2-fill', color: 'text-secondary' },
    48: { label: 'Icy Fog', icon: 'bi-cloud-fog2-fill', color: 'text-secondary' },
    51: { label: 'Light Drizzle', icon: 'bi-cloud-drizzle-fill', color: 'text-info' },
    53: { label: 'Moderate Drizzle', icon: 'bi-cloud-drizzle-fill', color: 'text-info' },
    55: { label: 'Heavy Drizzle', icon: 'bi-cloud-drizzle-fill', color: 'text-info' },
    61: { label: 'Light Rain', icon: 'bi-cloud-rain-fill', color: 'text-info' },
    63: { label: 'Moderate Rain', icon: 'bi-cloud-rain-fill', color: 'text-primary' },
    65: { label: 'Heavy Rain', icon: 'bi-cloud-rain-heavy-fill', color: 'text-primary' },
    71: { label: 'Light Snow', icon: 'bi-cloud-snow-fill', color: 'text-white' },
    73: { label: 'Moderate Snow', icon: 'bi-cloud-snow-fill', color: 'text-white' },
    75: { label: 'Heavy Snow', icon: 'bi-cloud-snow-fill', color: 'text-white' },
    80: { label: 'Rain Showers', icon: 'bi-cloud-rain-fill', color: 'text-info' },
    81: { label: 'Moderate Showers', icon: 'bi-cloud-rain-fill', color: 'text-primary' },
    82: { label: 'Violent Showers', icon: 'bi-cloud-lightning-rain-fill', color: 'text-danger' },
    95: { label: 'Thunderstorm', icon: 'bi-cloud-lightning-fill', color: 'text-danger' },
    99: { label: 'Severe Thunderstorm', icon: 'bi-cloud-lightning-rain-fill', color: 'text-danger' },
};

// Helper: re-centers the Leaflet map when coords change
function MapRecenter({ lat, lng }) {
    const map = useMap();
    useEffect(() => {
        if (lat && lng) {
            map.flyTo([lat, lng], 11, { duration: 1.5 });
        }
    }, [lat, lng, map]);
    return null;
}

// Geocode city name → { lat, lon } via Nominatim (free, no key required)
async function geocodeCity(query) {
    const url = `https://nominatim.openstreetmap.org/search?q=${encodeURIComponent(query)}&format=json&limit=1`;
    const res = await fetch(url, { headers: { 'Accept-Language': 'en' } });
    const data = await res.json();
    if (data && data.length > 0) {
        return { lat: parseFloat(data[0].lat), lon: parseFloat(data[0].lon), displayName: data[0].display_name };
    }
    return null;
}

// Fetch weather from Open-Meteo (free, no key required)
async function fetchWeather(lat, lon) {
    const url = `https://api.open-meteo.com/v1/forecast?latitude=${lat}&longitude=${lon}&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m,precipitation&wind_speed_unit=ms&timezone=auto`;
    const res = await fetch(url);
    const data = await res.json();
    if (data && data.current) {
        return {
            temp: Math.round(data.current.temperature_2m),
            humidity: data.current.relative_humidity_2m,
            windSpeed: data.current.wind_speed_10m.toFixed(1),
            precipitation: data.current.precipitation,
            code: data.current.weather_code,
        };
    }
    return null;
}

export default function DashboardHome() {
    const [loading, setLoading] = useState(true);
    const [searchQuery, setSearchQuery] = useState('');
    const [locationName, setLocationName] = useState('Pune, Maharashtra');
    const [pinCode, setPinCode] = useState('411001');
    const [soilType, setSoilType] = useState('');
    const [coords, setCoords] = useState({ lat: 18.5204, lng: 73.8567 });
    const [weather, setWeather] = useState(null);
    const [weatherLoading, setWeatherLoading] = useState(true);
    const reportRef = useRef();
    const { addSavedSearch } = React.useContext(SavedSearchContext);
    const { t } = useTranslation();

    // Initial load: fetch weather for default city (Pune)
    useEffect(() => {
        setSoilType(t('dashboard.soil_type') === 'Soil Type' ? 'Black Soil' : t('dashboard.soil_type'));
        loadWeather(coords.lat, coords.lng).finally(() => setLoading(false));
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [t]);

    async function loadWeather(lat, lng) {
        setWeatherLoading(true);
        try {
            const data = await fetchWeather(lat, lng);
            if (data) setWeather(data);
        } catch (err) {
            console.error('Weather fetch failed:', err);
        } finally {
            setWeatherLoading(false);
        }
    }

    const handleSearch = async (e) => {
        e.preventDefault();
        if (!searchQuery.trim()) return;

        setLoading(true);
        setWeatherLoading(true);
        try {
            const geo = await geocodeCity(searchQuery);
            if (geo) {
                setCoords({ lat: geo.lat, lng: geo.lon });
                // Use a cleaner display name (first two comma parts)
                const parts = geo.displayName.split(',');
                const cleanName = parts.slice(0, 2).join(',').trim();
                setLocationName(cleanName);
                setPinCode(Math.floor(100000 + Math.random() * 900000).toString());
                await loadWeather(geo.lat, geo.lon);
            } else {
                alert('Location not found. Please try a different search term.');
            }
        } catch (err) {
            console.error('Search error:', err);
        } finally {
            setLoading(false);
        }
    };

    const handleSaveLocation = () => {
        addSavedSearch({
            name: locationName,
            pin: pinCode,
            soil: 'Black Soil',
            date: new Date().toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })
        });
        alert(t('dashboard.location_saved'));
    };

    const handleGeneratePDF = () => {
        const element = reportRef.current;
        const opt = {
            margin:       10,
            filename:     'Soil_Health_Report.pdf',
            image:        { type: 'jpeg', quality: 0.98 },
            html2canvas:  { scale: 2, useCORS: true },
            jsPDF:        { unit: 'mm', format: 'a4', orientation: 'landscape' }
        };

        const buttons = element.querySelectorAll('.pdf-exclude');
        buttons.forEach(btn => btn.style.display = 'none');

        html2pdf().set(opt).from(element).save().then(() => {
            buttons.forEach(btn => btn.style.display = '');
        });
    };

    // Derived weather display info
    const wmoInfo = weather ? (WMO_CODES[weather.code] || { label: 'Unknown', icon: 'bi-cloud-fill', color: 'text-secondary' }) : null;

    if (loading && !weather && weatherLoading) {
        return (
            <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '60vh' }}>
                <CircularProgress color="success" />
            </Box>
        );
    }

    return (
        <Container fluid className="p-0">
            <Row className="g-4">
                {/* Main Content Column */}
                <Col lg={8} className="d-flex flex-column gap-4">
                    
                    {/* Smart Search Bar */}
                    <Card className="glass-panel border-0 text-white">
                        <Card.Body className="p-3">
                            <Form onSubmit={handleSearch}>
                                <InputGroup>
                                    <InputGroup.Text className="bg-transparent border-secondary text-secondary">
                                        <i className="bi bi-search"></i>
                                    </InputGroup.Text>
                                    <Form.Control
                                        type="text"
                                        placeholder={t('dashboard.smart_search_placeholder')}
                                        className="bg-transparent text-white border-secondary shadow-none"
                                        value={searchQuery}
                                        onChange={(e) => setSearchQuery(e.target.value)}
                                    />
                                    <Button variant="primary" type="submit" className="px-4 fw-bold border-0" style={{ background: 'linear-gradient(90deg, #2979ff, #1c54b2)' }} disabled={loading}>
                                        {loading ? <Spinner size="sm" /> : t('dashboard.search_btn')}
                                    </Button>
                                </InputGroup>
                            </Form>
                        </Card.Body>
                    </Card>

                    {/* Regional Survey Card */}
                    <div ref={reportRef}>
                        <Card className="glass-panel border-0 text-white">
                            <Card.Body className="p-4">
                                <div className="d-flex justify-content-between align-items-center mb-4">
                                    <h4 className="mb-0 fw-bold d-flex align-items-center gap-2">
                                        <i className="bi bi-geo-alt-fill text-danger"></i> 
                                        {t('dashboard.regional_survey')}: {locationName}
                                    </h4>
                                    <div className="d-flex gap-2 pdf-exclude">
                                        <Button variant="outline-light" size="sm" onClick={handleGeneratePDF} className="rounded-pill px-3 border-secondary text-white d-flex align-items-center gap-2 hover-white">
                                            <i className="bi bi-file-earmark-pdf-fill text-danger"></i> {t('dashboard.export_pdf')}
                                        </Button>
                                        <Button variant="outline-light" size="sm" onClick={handleSaveLocation} className="rounded-pill px-3 border-secondary text-white d-flex align-items-center gap-2 hover-white">
                                            <i className="bi bi-bookmark"></i> {t('dashboard.save_location')}
                                        </Button>
                                    </div>
                                </div>
                                
                                <Row className="g-3">
                                    <Col sm={6}>
                                        <div className="d-flex justify-content-between mb-3 border-bottom border-secondary pb-2" style={{ borderColor: 'rgba(255,255,255,0.05) !important' }}>
                                            <span className="text-light">{t('dashboard.pin_code')}:</span>
                                            <span className="fw-bold">{pinCode}</span>
                                        </div>
                                        <div className="d-flex justify-content-between mb-3 border-bottom border-secondary pb-2" style={{ borderColor: 'rgba(255,255,255,0.05) !important' }}>
                                            <span className="text-light">{t('dashboard.soil_type')}:</span>
                                            <span className="fw-bold">{soilType || 'Black Soil'}</span>
                                        </div>
                                        <div className="d-flex justify-content-between mb-3 border-bottom border-secondary pb-2" style={{ borderColor: 'rgba(255,255,255,0.05) !important' }}>
                                            <span className="text-light">{t('dashboard.flood_risk')}:</span>
                                            <span className="fw-bold text-success">{t('dashboard.low')}</span>
                                        </div>
                                        <div className="d-flex justify-content-between mb-3 border-bottom border-secondary pb-2" style={{ borderColor: 'rgba(255,255,255,0.05) !important' }}>
                                            <span className="text-light">{t('dashboard.avg_rainfall')}:</span>
                                            <span className="fw-bold">700 {t('dashboard.mm')}</span>
                                        </div>
                                    </Col>
                                    <Col sm={6}>
                                        <div className="d-flex justify-content-between mb-3 border-bottom border-secondary pb-2" style={{ borderColor: 'rgba(255,255,255,0.05) !important' }}>
                                            <span className="text-light">{t('dashboard.groundwater')}:</span>
                                            <span className="fw-bold text-warning">{t('dashboard.semi_critical')}</span>
                                        </div>
                                        <div className="d-flex justify-content-between mb-3 border-bottom border-secondary pb-2" style={{ borderColor: 'rgba(255,255,255,0.05) !important' }}>
                                            <span className="text-light">{t('dashboard.borewell_depth')}:</span>
                                            <span className="fw-bold">120 {t('dashboard.meters')}</span>
                                        </div>
                                        <div className="d-flex justify-content-between mb-3 border-bottom border-secondary pb-2" style={{ borderColor: 'rgba(255,255,255,0.05) !important' }}>
                                            <span className="text-light">{t('dashboard.water_retention')}:</span>
                                            <span className="fw-bold">{t('dashboard.high')}</span>
                                        </div>
                                        <div className="d-flex justify-content-between mb-3 border-bottom border-secondary pb-2" style={{ borderColor: 'rgba(255,255,255,0.05) !important' }}>
                                            <span className="text-light">{t('dashboard.soil_drainage')}:</span>
                                            <span className="fw-bold">{t('dashboard.moderate')}</span>
                                        </div>
                                    </Col>
                                </Row>
                            </Card.Body>
                        </Card>
                    </div>

                    {/* Live Map Card — centers on searched location */}
                    <Card className="glass-panel border-0 text-white flex-grow-1" style={{ minHeight: '400px' }}>
                        <Card.Body className="p-4 d-flex flex-column">
                            <h5 className="fw-bold mb-1 d-flex align-items-center gap-2">
                                <i className="bi bi-map"></i> {t('dashboard.gis_title')}
                            </h5>
                            <p className="text-secondary small mb-3">{t('dashboard.gis_desc')}</p>
                            
                            <div className="flex-grow-1 rounded overflow-hidden border border-secondary" style={{ minHeight: '350px', borderColor: 'rgba(255,255,255,0.1) !important' }}>
                                <MapContainer center={[coords.lat, coords.lng]} zoom={11} style={{ height: '100%', width: '100%', minHeight: '350px' }}>
                                    <TileLayer
                                        url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
                                        attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
                                    />
                                    {/* Smoothly re-centers/flies to new coords on search */}
                                    <MapRecenter lat={coords.lat} lng={coords.lng} />
                                    <Marker position={[coords.lat, coords.lng]}>
                                        <Popup>
                                            <strong>{locationName}</strong><br/>
                                            {coords.lat.toFixed(4)}°N, {coords.lng.toFixed(4)}°E
                                        </Popup>
                                    </Marker>
                                </MapContainer>
                            </div>
                        </Card.Body>
                    </Card>
                </Col>

                {/* Right Sidebar Column */}
                <Col lg={4} className="d-flex flex-column gap-4">
                    
                    {/* Live Weather Card — Open-Meteo API */}
                    <Card className="glass-panel border-0 text-white">
                        <Card.Body className="p-4">
                            <h6 className="fw-bold mb-3 d-flex align-items-center gap-2">
                                <i className="bi bi-cloud-sun text-success"></i> {t('dashboard.weather_title')}
                                {weatherLoading && <Spinner size="sm" variant="success" className="ms-auto" />}
                            </h6>

                            {weather && !weatherLoading ? (
                                <>
                                    <div className="d-flex justify-content-between align-items-center mb-4">
                                        <div>
                                            <h1 className="display-4 fw-bold mb-0">{weather.temp}°C</h1>
                                            <p className="text-secondary mb-0">{wmoInfo.label}</p>
                                            <p className="text-secondary small mb-0" style={{ fontSize: '0.7rem' }}>
                                                <i className="bi bi-geo-alt-fill me-1 text-danger"></i>
                                                {locationName}
                                            </p>
                                        </div>
                                        <i className={`bi ${wmoInfo.icon} ${wmoInfo.color}`} style={{ fontSize: '3rem' }}></i>
                                    </div>
                                    
                                    <div className="d-flex justify-content-between mb-3 border-bottom border-secondary pb-3" style={{ borderColor: 'rgba(255,255,255,0.1) !important' }}>
                                        <div>
                                            <div className="text-secondary small">{t('dashboard.humidity')}:</div>
                                            <div className="fw-bold">{weather.humidity}%</div>
                                        </div>
                                        <div>
                                            <div className="text-secondary small">{t('dashboard.wind_speed')}:</div>
                                            <div className="fw-bold">{weather.windSpeed} m/s</div>
                                        </div>
                                        <div>
                                            <div className="text-secondary small">Precipitation:</div>
                                            <div className="fw-bold">{weather.precipitation} mm</div>
                                        </div>
                                    </div>

                                    <div className="d-flex align-items-center gap-2 mb-2">
                                        <Badge bg="success" className="rounded-pill px-2">
                                            <i className="bi bi-broadcast me-1"></i>Live
                                        </Badge>
                                        <small className="text-secondary">via Open-Meteo · {new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</small>
                                    </div>
                                    
                                    <p className="text-info small mb-0 d-flex gap-2">
                                        <i className="bi bi-info-circle-fill"></i>
                                        {t('dashboard.weather_tip')}
                                    </p>
                                </>
                            ) : weatherLoading ? (
                                <div className="text-center py-4 text-secondary">
                                    <Spinner variant="success" className="mb-2" />
                                    <p className="small mb-0">Fetching live weather…</p>
                                </div>
                            ) : (
                                <div className="text-secondary text-center py-3">
                                    <i className="bi bi-exclamation-triangle-fill text-warning d-block mb-2" style={{ fontSize: '2rem' }}></i>
                                    <small>Weather data unavailable</small>
                                </div>
                            )}
                        </Card.Body>
                    </Card>

                    {/* Agriculture Services List */}
                    <Card className="glass-panel border-0 text-white flex-grow-1">
                        <Card.Body className="p-4">
                            <h6 className="fw-bold mb-4 d-flex align-items-center gap-2">
                                <i className="bi bi-journal-text"></i> {t('dashboard.agri_services')}
                            </h6>
                            
                            <div className="d-flex flex-column gap-3">
                                {/* Service 1 */}
                                <div className="p-3 rounded border border-secondary" style={{ borderColor: 'rgba(255,255,255,0.1) !important', background: 'rgba(0,0,0,0.2)' }}>
                                    <div className="d-flex justify-content-between align-items-start mb-2">
                                        <h6 className="fw-bold mb-0">{t('dashboard.service1_name')}</h6>
                                        <Badge bg="primary" className="text-white">{t('dashboard.service1_type')}</Badge>
                                    </div>
                                    <p className="text-secondary small mb-2">{t('dashboard.service1_addr')}, {locationName}</p>
                                    <a href="tel:020-25698421" className="text-success text-decoration-none small fw-bold"><i className="bi bi-telephone-fill"></i> {t('dashboard.call')}: 020-25698421</a>
                                </div>

                                {/* Service 2 */}
                                <div className="p-3 rounded border border-secondary" style={{ borderColor: 'rgba(255,255,255,0.1) !important', background: 'rgba(0,0,0,0.2)' }}>
                                    <div className="d-flex justify-content-between align-items-start mb-2">
                                        <h6 className="fw-bold mb-0">{t('dashboard.service2_name')}</h6>
                                        <Badge bg="danger" className="text-white">{t('dashboard.service2_type')}</Badge>
                                    </div>
                                    <p className="text-secondary small mb-2">{t('dashboard.service2_addr')}, {locationName}</p>
                                    <a href="tel:9845012345" className="text-success text-decoration-none small fw-bold"><i className="bi bi-telephone-fill"></i> {t('dashboard.call')}: 9845012345</a>
                                </div>

                                {/* Service 3 */}
                                <div className="p-3 rounded border border-secondary" style={{ borderColor: 'rgba(255,255,255,0.1) !important', background: 'rgba(0,0,0,0.2)' }}>
                                    <div className="d-flex justify-content-between align-items-start mb-2">
                                        <h6 className="fw-bold mb-0">{t('dashboard.service3_name')}</h6>
                                        <Badge bg="success" className="text-white">{t('dashboard.service3_type')}</Badge>
                                    </div>
                                    <p className="text-secondary small mb-2">{t('dashboard.service3_addr')}, {locationName}</p>
                                    <a href="tel:0253-2578491" className="text-success text-decoration-none small fw-bold"><i className="bi bi-telephone-fill"></i> {t('dashboard.call')}: 0253-2578491</a>
                                </div>
                            </div>
                        </Card.Body>
                    </Card>
                    
                </Col>
            </Row>
            <InsightsFooter />
        </Container>
    );
}
