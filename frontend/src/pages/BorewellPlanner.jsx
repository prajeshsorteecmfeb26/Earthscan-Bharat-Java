import React, { useRef, useState, useEffect } from 'react';
import { Container, Row, Col, Card, Form, Button, ProgressBar } from 'react-bootstrap';
import { MapContainer, TileLayer, Marker, Popup, useMap } from 'react-leaflet';
import 'leaflet/dist/leaflet.css';
import L from 'leaflet';
import InsightsFooter from '../components/InsightsFooter';
import html2pdf from 'html2pdf.js';
import { CircularProgress } from '@mui/material';
import { useTranslation } from 'react-i18next';

// Fix leaflet default marker icons
delete L.Icon.Default.prototype._getIconUrl;
L.Icon.Default.mergeOptions({
    iconRetinaUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-icon-2x.png',
    iconUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-icon.png',
    shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-shadow.png',
});

// Re-centers Leaflet map on coord change
function MapRecenter({ lat, lng }) {
    const map = useMap();
    useEffect(() => {
        if (lat && lng) map.flyTo([lat, lng], 12, { duration: 1.2 });
    }, [lat, lng, map]);
    return null;
}

// Geocode city+area → { lat, lon } via Nominatim
async function geocodeLocation(city, area) {
    const query = area ? `${area}, ${city}, India` : `${city}, India`;
    const url = `https://nominatim.openstreetmap.org/search?q=${encodeURIComponent(query)}&format=json&limit=1`;
    try {
        const res = await fetch(url, { headers: { 'Accept-Language': 'en' } });
        const data = await res.json();
        if (data && data.length > 0) {
            return { lat: parseFloat(data[0].lat), lon: parseFloat(data[0].lon) };
        }
    } catch (e) {
        console.error('Geocode failed:', e);
    }
    return null;
}

export default function BorewellPlanner() {
    const reportRef = useRef();
    const { t } = useTranslation();

    const [city, setCity] = useState('');
    const [area, setArea] = useState('');
    const [pin, setPin] = useState('');
    const [landSize, setLandSize] = useState('');
    const [waterReq, setWaterReq] = useState('');
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');

    // Initial mock data state
    const [results, setResults] = useState(null);
    const [mapCoords, setMapCoords] = useState(null); // { lat, lng } for Leaflet map
    const [mapLabel, setMapLabel] = useState('');

    // Load state from session storage on mount
    useEffect(() => {
        const saved = sessionStorage.getItem('borewellPlannerState');
        if (saved) {
            try {
                const state = JSON.parse(saved);
                if (state.city) setCity(state.city);
                if (state.area) setArea(state.area);
                if (state.pin) setPin(state.pin);
                if (state.landSize) setLandSize(state.landSize);
                if (state.waterReq) setWaterReq(state.waterReq);
                if (state.results) setResults(state.results);
            } catch (e) {
                console.error("Failed to parse session storage", e);
            }
        }
    }, []);

    // Save state to session storage whenever it changes
    useEffect(() => {
        sessionStorage.setItem('borewellPlannerState', JSON.stringify({
            city, area, pin, landSize, waterReq, results
        }));
    }, [city, area, pin, landSize, waterReq, results]);


    const handleGeneratePDF = async () => {
        const element = reportRef.current;
        const opt = {
            margin: 10,
            filename: 'Borewell_Intelligence_Report.pdf',
            image: { type: 'jpeg', quality: 0.98 },
            html2canvas: { scale: 2, useCORS: true, logging: false },
            jsPDF: { unit: 'mm', format: 'a4', orientation: 'landscape' }
        };

        const buttons = element.querySelectorAll('.pdf-exclude');
        buttons.forEach(btn => btn.style.display = 'none');

        try {
            // In Vite, html2pdf might be on the .default property
            const generatePdf = typeof html2pdf === 'function' ? html2pdf : html2pdf.default;
            await generatePdf().set(opt).from(element).save();
        } catch (error) {
            console.error("PDF generation failed:", error);
            alert("Failed to generate PDF. Please check the console for details.");
        } finally {
            buttons.forEach(btn => btn.style.display = '');
        }
    };

    const handleAnalyze = () => {
        if (!city || !area || !pin || !landSize || !waterReq) {
            setError(t('borewell.error_fill_all'));
            return;
        }
        const pinRegex = /^[0-9]{6}$/;
        if (!pinRegex.test(pin)) {
            setError(t('borewell.error_pin'));
            return;
        }
        const numLand = Number(landSize);
        const numWater = Number(waterReq);
        if (numLand <= 0 || numLand > 5000) {
            setError(t('borewell.error_land'));
            return;
        }
        if (numWater <= 0 || numWater > 1000000) {
            setError(t('borewell.error_water'));
            return;
        }

        setError('');
        setLoading(true);

        // Geocode city for the live map (runs in parallel with analysis)
        geocodeLocation(city, area).then(geo => {
            if (geo) {
                setMapCoords({ lat: geo.lat, lng: geo.lon });
                setMapLabel(`${area ? area + ', ' : ''}${city}`);
            }
        });

        setTimeout(() => {
            // Generate some random but plausible numbers based on inputs
            const newSuccessRate = Math.floor(Math.random() * (95 - 40 + 1) + 40);
            
            // Adjust cost based on land size loosely
            const baseCost = 30000;
            const newCost = baseCost + (landSize * 2000) + Math.floor(Math.random() * 15000);

            // Adjust yield randomly
            const yields = ['0.5 - 1.0', '1.0 - 1.5', '1.5 - 2.0', '2.0 - 3.0', '3.0+'];
            const newYield = yields[Math.floor(Math.random() * yields.length)];

            // Randomize depth probabilities based on overall success rate
            const surfaceP = Math.floor(Math.random() * 30);
            const fracturedP = Math.floor(Math.random() * (60 - 30) + 30);
            const deepP = newSuccessRate; 

            setResults({
                yield: newYield,
                successRate: newSuccessRate,
                cost: `₹${newCost.toLocaleString()}`,
                depths: [
                    { label: '50 - 100 feet (Surface Water)', p: surfaceP, variant: surfaceP > 20 ? 'warning' : 'danger' },
                    { label: '100 - 200 feet (Fractured Rock)', p: fracturedP, variant: fracturedP > 40 ? 'success' : 'warning' },
                    { label: '200 - 350 feet (Deep Aquifer) - Recommended', p: deepP, variant: deepP > 70 ? 'success' : (deepP > 50 ? 'warning' : 'danger') }
                ]
            });
            
            setLoading(false);
        }, 1200); // Simulate network/AI delay
    };

    return (
        <Container fluid className="p-0">
            <div className="d-flex justify-content-between align-items-center mb-4">
                <h2 className="text-white fw-bold mb-0">
                    <i className="bi bi-droplet-fill text-info"></i> {t('borewell.title')}
                </h2>
                <Button 
                    variant="outline-light" 
                    className="rounded-pill px-4 hover-white d-flex align-items-center gap-2"
                    onClick={handleGeneratePDF}
                >
                    <i className="bi bi-file-earmark-pdf-fill text-danger"></i> {t('borewell.export_report')}
                </Button>
            </div>
            <div ref={reportRef}>
            <Row className="g-4">
                <Col lg={4}>
                    <Card className="glass-panel border-0 text-white h-100">
                        <Card.Body className="p-4">
                            <h5 className="fw-bold mb-3">{t('borewell.site_params')}</h5>
                            <Form>
                                <Row className="g-2 mb-3">
                                    <Col sm={6}>
                                        <Form.Group>
                                            <Form.Label className="text-secondary small">{t('borewell.city')}</Form.Label>
                                            <Form.Control type="text" value={city} onChange={e => setCity(e.target.value)} placeholder="e.g. Pune" className="bg-transparent text-white border-secondary shadow-none" />
                                        </Form.Group>
                                    </Col>
                                    <Col sm={6}>
                                        <Form.Group>
                                            <Form.Label className="text-secondary small">{t('borewell.area')}</Form.Label>
                                            <Form.Control type="text" value={area} onChange={e => setArea(e.target.value)} placeholder="e.g. Kothrud" className="bg-transparent text-white border-secondary shadow-none" />
                                        </Form.Group>
                                    </Col>
                                </Row>
                                <Form.Group className="mb-3">
                                    <Form.Label className="text-secondary small">{t('borewell.pin_code')}</Form.Label>
                                    <Form.Control type="text" value={pin} onChange={e => setPin(e.target.value)} placeholder="e.g. 411038" className="bg-transparent text-white border-secondary shadow-none" />
                                </Form.Group>
                                <Form.Group className="mb-3">
                                    <Form.Label className="text-secondary small">{t('borewell.land_size')}</Form.Label>
                                    <Form.Control type="number" value={landSize} onChange={e => setLandSize(Number(e.target.value))} placeholder="5" className="bg-transparent text-white border-secondary shadow-none" />
                                </Form.Group>
                                <Form.Group className="mb-4">
                                    <Form.Label className="text-secondary small">{t('borewell.water_req')}</Form.Label>
                                    <Form.Control type="number" value={waterReq} onChange={e => setWaterReq(Number(e.target.value))} placeholder="5000" className="bg-transparent text-white border-secondary shadow-none" />
                                </Form.Group>
                                <Button 
                                    variant="primary" 
                                    className="w-100 py-2 fw-bold border-0 pdf-exclude d-flex justify-content-center align-items-center gap-2" 
                                    style={{ background: 'linear-gradient(90deg, #00b4db, #0083b0)' }}
                                    onClick={handleAnalyze}
                                    disabled={loading}
                                >
                                    {loading ? <CircularProgress size={20} color="inherit" /> : null}
                                    {loading ? t('borewell.scanning') : t('borewell.analyze_btn')}
                                </Button>
                                {error && <div className="text-danger small mt-2 fw-bold text-center"><i className="bi bi-exclamation-triangle-fill"></i> {error}</div>}
                            </Form>
                        </Card.Body>
                    </Card>
                </Col>
                <Col lg={8}>
                    {results ? (
                        <Card className="glass-panel border-0 text-white mb-4">
                            <Card.Body className="p-4">
                                <h5 className="fw-bold mb-4">{t('borewell.results_title')}</h5>
                                <Row className="g-4 mb-4">
                                    <Col md={4}>
                                        <div className="p-3 rounded border border-secondary text-center" style={{ background: 'rgba(0,0,0,0.2)' }}>
                                            <h6 className="text-secondary mb-2">{t('borewell.est_yield')}</h6>
                                            <h3 className="fw-bold text-success mb-0">{results.yield}</h3>
                                            <small className="text-secondary">{t('borewell.inches_water')}</small>
                                        </div>
                                    </Col>
                                    <Col md={4}>
                                        <div className="p-3 rounded border border-secondary text-center" style={{ background: 'rgba(0,0,0,0.2)' }}>
                                            <h6 className="text-secondary mb-2">{t('borewell.success_rate')}</h6>
                                            <h3 className={`fw-bold mb-0 ${results.successRate >= 75 ? 'text-success' : (results.successRate >= 50 ? 'text-warning' : 'text-danger')}`}>
                                                {results.successRate}%
                                            </h3>
                                            <small className="text-secondary">{t('borewell.hydro_data')}</small>
                                        </div>
                                    </Col>
                                    <Col md={4}>
                                        <div className="p-3 rounded border border-secondary text-center" style={{ background: 'rgba(0,0,0,0.2)' }}>
                                            <h6 className="text-secondary mb-2">{t('borewell.est_cost')}</h6>
                                            <h3 className="fw-bold text-info mb-0">{results.cost}</h3>
                                            <small className="text-secondary">{t('borewell.optimal_depth')}</small>
                                        </div>
                                    </Col>
                                </Row>
                                <h6 className="fw-bold mb-3">{t('borewell.depth_prob')}</h6>
                                {results.depths.map((depth, index) => (
                                    <div className="mb-3" key={index}>
                                        <div className="d-flex justify-content-between mb-1">
                                            <span className="text-secondary small">{depth.label}</span>
                                            <span className={`text-${depth.variant} small fw-bold`}>{depth.p}%</span>
                                        </div>
                                        <ProgressBar variant={depth.variant} now={depth.p} style={{ height: '8px', background: '#2c3e50' }} />
                                    </div>
                                ))}
                            </Card.Body>
                        </Card>
                    ) : (
                        <div className="h-100 d-flex flex-column justify-content-center align-items-center text-secondary border border-secondary rounded glass-panel p-5 text-center" style={{ minHeight: '300px', borderColor: 'rgba(255,255,255,0.1) !important' }}>
                            <i className="bi bi-droplet-half mb-3 text-info opacity-50" style={{ fontSize: '3rem' }}></i>
                            <h5 className="fw-bold text-white">{t('borewell.awaiting')}</h5>
                            <p className="mb-0 mx-auto" style={{ maxWidth: '400px' }}>{t('borewell.awaiting_desc')}</p>
                        </div>
                    )}

                    {/* Live Leaflet Map — appears after geocoding */}
                    {mapCoords && (
                        <Card className="glass-panel border-0 text-white mt-4">
                            <Card.Body className="p-4">
                                <h6 className="fw-bold mb-1 d-flex align-items-center gap-2">
                                    <i className="bi bi-map text-info"></i> Site Location Map
                                    <small className="text-secondary fw-normal ms-1">— {mapLabel}</small>
                                </h6>
                                <p className="text-secondary small mb-3">
                                    Showing {mapLabel} · {mapCoords.lat.toFixed(4)}°N, {mapCoords.lng.toFixed(4)}°E
                                </p>
                                <div className="rounded overflow-hidden" style={{ height: '300px', border: '1px solid rgba(255,255,255,0.1)' }}>
                                    <MapContainer
                                        center={[mapCoords.lat, mapCoords.lng]}
                                        zoom={12}
                                        style={{ height: '100%', width: '100%' }}
                                    >
                                        <TileLayer
                                            url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
                                            attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
                                        />
                                        <MapRecenter lat={mapCoords.lat} lng={mapCoords.lng} />
                                        <Marker position={[mapCoords.lat, mapCoords.lng]}>
                                            <Popup>
                                                <strong>📍 {mapLabel}</strong><br/>
                                                Borewell Analysis Site<br/>
                                                <small>{mapCoords.lat.toFixed(4)}°N, {mapCoords.lng.toFixed(4)}°E</small>
                                            </Popup>
                                        </Marker>
                                    </MapContainer>
                                </div>
                            </Card.Body>
                        </Card>
                    )}
                </Col>
            </Row>
            </div>
            <InsightsFooter />
        </Container>
    );
}
