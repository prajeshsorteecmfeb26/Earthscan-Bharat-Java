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
import { getInstantRegionalSurveyData, fetchRegionalSurveyData } from '../utils/regionalSurveyUtils';

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

// Geocode city name → { lat, lon, displayName, address } via Nominatim (free, no key required)
async function geocodeCity(query) {
    const url = `https://nominatim.openstreetmap.org/search?q=${encodeURIComponent(query)}&format=json&addressdetails=1&limit=1`;
    const res = await fetch(url, { headers: { 'Accept-Language': 'en', 'User-Agent': 'EarthScanBharat/1.0' } });
    const data = await res.json();
    if (data && data.length > 0) {
        return {
            lat: parseFloat(data[0].lat),
            lon: parseFloat(data[0].lon),
            displayName: data[0].display_name,
            address: data[0].address
        };
    }
    return null;
}

// Multi-tiered PIN code fetcher using accurate web APIs
async function fetchAccuratePinCode(lat, lon, query, addressData) {
    if (addressData?.postcode) {
        return addressData.postcode;
    }

    try {
        const revUrl = `https://nominatim.openstreetmap.org/reverse?lat=${lat}&lon=${lon}&format=json&addressdetails=1`;
        const revRes = await fetch(revUrl, { headers: { 'Accept-Language': 'en', 'User-Agent': 'EarthScanBharat/1.0' } });
        const revData = await revRes.json();
        if (revData?.address?.postcode) {
            return revData.address.postcode;
        }
    } catch (e) {
        console.warn('Nominatim reverse lookup failed:', e);
    }

    try {
        const cleanQuery = query.split(',')[0].trim();
        const address = addressData || {};
        const district = (address.state_district || address.county || address.city || address.town || cleanQuery).toLowerCase();
        const state = (address.state || '').toLowerCase();

        const postUrl = `https://api.postalpincode.in/postoffice/${encodeURIComponent(cleanQuery)}`;
        const postRes = await fetch(postUrl);
        const postData = await postRes.json();

        if (postData && postData[0]?.Status === 'Success' && postData[0]?.PostOffice?.length > 0) {
            const offices = postData[0].PostOffice;
            let match = offices.find(po => po.District.toLowerCase() === district);
            if (!match) {
                match = offices.find(po => po.District.toLowerCase().includes(district) || district.includes(po.District.toLowerCase()));
            }
            if (!match && state) {
                match = offices.find(po => po.State.toLowerCase() === state);
            }
            if (match?.Pincode) {
                return match.Pincode;
            }
        }
    } catch (e) {
        console.warn('India Post API lookup failed:', e);
    }

    try {
        const bdcUrl = `https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=${lat}&longitude=${lon}&localityLanguage=en`;
        const bdcRes = await fetch(bdcUrl);
        const bdcData = await bdcRes.json();
        if (bdcData?.postcode) {
            return bdcData.postcode;
        }
    } catch (e) {
        console.warn('BigDataCloud API lookup failed:', e);
    }

    return 'N/A';
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
    const [surveyData, setSurveyData] = useState(() => getInstantRegionalSurveyData('Pune'));

    const reportRef = useRef();
    const { addSavedSearch } = React.useContext(SavedSearchContext);
    const { t } = useTranslation();

    // Initial load: fetch weather and regional survey for default city (Pune)
    useEffect(() => {
        loadWeather(coords.lat, coords.lng);
        loadSurveyData(coords.lat, coords.lng, 'Pune, Maharashtra', { state: 'Maharashtra' }).finally(() => setLoading(false));
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

    async function loadSurveyData(lat, lng, locName = '', addressObj = {}) {
        // Step 1: Immediately set 0ms instant baseline values!
        const instant = getInstantRegionalSurveyData(locName);
        setSurveyData({
            ...instant,
            loading: false
        });
        setSoilType(instant.soilType);

        // Step 2: Refine in background asynchronously
        try {
            const data = await fetchRegionalSurveyData(lat, lng, locName, addressObj);
            setSurveyData(prev => ({
                ...prev,
                ...data,
                loading: false
            }));
            setSoilType(data.soilType);
        } catch (err) {
            // Baseline is already set
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
                const parts = geo.displayName.split(',');
                const cleanName = parts.slice(0, 2).join(',').trim();
                setLocationName(cleanName);

                const pinPromise = fetchAccuratePinCode(geo.lat, geo.lon, searchQuery, geo.address);
                const weatherPromise = loadWeather(geo.lat, geo.lon);
                const surveyPromise = loadSurveyData(geo.lat, geo.lon, cleanName, geo.address);

                const resolvedPin = await pinPromise;
                setPinCode(resolvedPin);
                await Promise.all([weatherPromise, surveyPromise]);
            } else {
                alert('Location not found. Please try a different search term.');
            }
        } catch (err) {
            console.error('Search error:', err);
        } finally {
            setLoading(false);
        }
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
        <Container fluid className="p-0 d-flex flex-column gap-4">
            {/* Row 1: Smart Search Bar (Full Width 12 cols) */}
            <Row>
                <Col lg={12}>
                    <Card className="glass-panel border-0 text-white shadow-sm">
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
                </Col>
            </Row>

            {/* Row 2: Regional Survey (8 cols) + Weather Intelligence (4 cols) */}
            <Row className="g-4 align-items-stretch">
                <Col lg={8} ref={reportRef}>
                    <Card className="glass-panel border-0 text-white h-100">
                        <Card.Body className="p-4 d-flex flex-column justify-content-between">
                            <div>
                                <div className="d-flex justify-content-between align-items-center mb-4">
                                    <h4 className="mb-0 fw-bold d-flex align-items-center gap-2">
                                        <i className="bi bi-geo-alt-fill text-danger"></i> 
                                        {t('dashboard.regional_survey')}: {locationName}
                                    </h4>
                                    <div className="d-flex gap-2 pdf-exclude">
                                        <Button variant="outline-light" size="sm" onClick={handleGeneratePDF} className="rounded-pill px-3 py-1 text-nowrap d-flex align-items-center gap-1_5 border-secondary text-white hover-white" style={{ fontSize: '0.8rem', fontWeight: 500 }}>
                                            <i className="bi bi-file-earmark-pdf-fill text-danger"></i> {t('dashboard.export_pdf')}
                                        </Button>
                                    </div>
                                </div>
                                
                                <Row className="g-4 py-3 flex-grow-1">
                                    <Col sm={6} className="d-flex flex-column justify-content-between">
                                        <div className="d-flex justify-content-between align-items-center py-3 border-bottom border-secondary" style={{ borderColor: 'rgba(255,255,255,0.08) !important' }}>
                                            <span className="text-light fs-6">{t('dashboard.pin_code')}:</span>
                                            <span className="fw-bold fs-5">{pinCode}</span>
                                        </div>
                                        <div className="d-flex justify-content-between align-items-center py-3 border-bottom border-secondary" style={{ borderColor: 'rgba(255,255,255,0.08) !important' }}>
                                            <span className="text-light fs-6">{t('dashboard.soil_type')}:</span>
                                            <span className="fw-bold fs-6">{surveyData.soilType || 'Black Cotton Soil'}</span>
                                        </div>
                                        <div className="d-flex justify-content-between align-items-center py-3 border-bottom border-secondary" style={{ borderColor: 'rgba(255,255,255,0.08) !important' }}>
                                            <span className="text-light fs-6">GW Recharge:</span>
                                            <span className="fw-bold fs-6 text-success">
                                                {surveyData.gwRechargeBCM || '44.10 BCM'}
                                            </span>
                                        </div>
                                    </Col>
                                    <Col sm={6} className="d-flex flex-column justify-content-between">
                                        <div className="d-flex justify-content-between align-items-center py-3 border-bottom border-secondary" style={{ borderColor: 'rgba(255,255,255,0.08) !important' }}>
                                            <span className="text-light fs-6">{t('dashboard.groundwater')}:</span>
                                            <span className={`fw-bold fs-6 ${surveyData.groundwaterVariant || 'text-success'}`}>
                                                {surveyData.groundwaterStatusFull || 'Safe (50.0%)'}
                                            </span>
                                        </div>
                                        <div className="d-flex justify-content-between align-items-center py-3 border-bottom border-secondary" style={{ borderColor: 'rgba(255,255,255,0.08) !important' }}>
                                            <span className="text-light fs-6">{t('dashboard.borewell_depth')}:</span>
                                            <span className="fw-bold fs-6">
                                                {surveyData.borewellDepthFeet || '100 - 150 feet'}
                                            </span>
                                        </div>
                                        <div className="d-flex justify-content-between align-items-center py-3 border-bottom border-secondary" style={{ borderColor: 'rgba(255,255,255,0.08) !important' }}>
                                            <span className="text-light fs-6">Avg Annual Rainfall:</span>
                                            <span className="fw-bold fs-6" style={{ color: '#00bcd4' }}>
                                                {`${surveyData.avgRainfall || 688} ${t('dashboard.mm')}`}
                                            </span>
                                        </div>
                                    </Col>
                                </Row>
                            </div>
                        </Card.Body>
                    </Card>
                </Col>

                <Col lg={4}>
                    <Card className="glass-panel border-0 text-white h-100">
                        <Card.Body className="p-4 d-flex flex-column justify-content-between">
                            <div>
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
                            </div>
                        </Card.Body>
                    </Card>
                </Col>
            </Row>

            {/* Row 3: Geospatial GIS Mapping Explorer (Full Width 12 cols) */}
            <Row>
                <Col lg={12}>
                    <Card className="glass-panel border-0 text-white">
                        <Card.Body className="p-4 d-flex flex-column">
                            <h5 className="fw-bold mb-1 d-flex align-items-center gap-2">
                                <i className="bi bi-map"></i> {t('dashboard.gis_title')}
                            </h5>
                            <p className="text-secondary small mb-3">{t('dashboard.gis_desc')}</p>
                            
                            <div className="rounded overflow-hidden border border-secondary" style={{ height: '420px', borderColor: 'rgba(255,255,255,0.1) !important' }}>
                                <MapContainer center={[coords.lat, coords.lng]} zoom={11} style={{ height: '100%', width: '100%' }}>
                                    <TileLayer
                                        url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
                                        attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
                                    />
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
            </Row>

            <InsightsFooter />
        </Container>
    );
}
