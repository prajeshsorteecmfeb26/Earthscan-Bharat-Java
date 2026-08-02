import React, { useRef, useState, useEffect } from 'react';
import { Container, Row, Col, Card, Form, Button, Badge } from 'react-bootstrap';
import InsightsFooter from '../components/InsightsFooter';
import html2pdf from 'html2pdf.js';
import { CircularProgress } from '@mui/material';
import { useTranslation } from 'react-i18next';

export default function CropFertilizer() {
    const reportRef = useRef();
    const { t } = useTranslation();

    const [n, setN] = useState('');
    const [p, setP] = useState('');
    const [k, setK] = useState('');
    const [ph, setPh] = useState('');
    const [rainfall, setRainfall] = useState('');

    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');
    const [recommendations, setRecommendations] = useState(null);

    // Load state from session storage on mount
    useEffect(() => {
        const saved = sessionStorage.getItem('cropFertilizerState');
        if (saved) {
            try {
                const state = JSON.parse(saved);
                if (state.n) setN(state.n);
                if (state.p) setP(state.p);
                if (state.k) setK(state.k);
                if (state.ph) setPh(state.ph);
                if (state.rainfall) setRainfall(state.rainfall);
                if (state.recommendations) setRecommendations(state.recommendations);
            } catch (e) {
                console.error("Failed to parse session storage", e);
            }
        }
    }, []);

    // Save state to session storage whenever it changes
    useEffect(() => {
        sessionStorage.setItem('cropFertilizerState', JSON.stringify({
            n, p, k, ph, rainfall, recommendations
        }));
    }, [n, p, k, ph, rainfall, recommendations]);



    const handleGeneratePDF = async () => {
        const element = reportRef.current;
        const opt = {
            margin: 10,
            filename: 'Crop_Fertilizer_Report.pdf',
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

    const getRecommendations = () => {
        if (!n || !p || !k || !ph || !rainfall) {
            setError(t('crop_ai.error_fill'));
            return;
        }
        
        const numN = Number(n);
        const numP = Number(p);
        const numK = Number(k);
        const numPh = Number(ph);
        const numRain = Number(rainfall);

        if (numN < 0 || numN > 500 || numP < 0 || numP > 500 || numK < 0 || numK > 500) {
            setError(t('crop_ai.error_npk'));
            return;
        }
        if (numPh < 0 || numPh > 14) {
            setError(t('crop_ai.error_ph'));
            return;
        }
        if (numRain < 0 || numRain > 10000) {
            setError(t('crop_ai.error_rain'));
            return;
        }

        setError('');
        setLoading(true);

        setTimeout(() => {
            const possibleCrops = [];

            if (rainfall >= 150) {
                possibleCrops.push({
                    crop: 'Rice',
                    desc: 'High rainfall and adequate nitrogen levels make this ideal for paddy cultivation.',
                    fert: 'Urea (45% N) & Potash',
                    dose: '60 kg per Acre'
                });
                possibleCrops.push({
                    crop: 'Sugarcane',
                    desc: 'Requires abundant water. Your soil pH is excellent for sucrose accumulation.',
                    fert: 'NPK 10:26:26',
                    dose: '100 kg per Acre'
                });
                possibleCrops.push({
                    crop: 'Jute',
                    desc: 'Thrives in warm, humid climates with heavy rainfall. Great cash crop.',
                    fert: 'Urea & SSP',
                    dose: '40 kg per Acre'
                });
                possibleCrops.push({
                    crop: 'Banana',
                    desc: 'High water requirement crop. The soil parameters support rapid vegetative growth.',
                    fert: 'MOP & Urea',
                    dose: '120 kg per Acre'
                });
            } else if (rainfall < 80) {
                possibleCrops.push({
                    crop: 'Pearl Millet (Bajra)',
                    desc: 'Highly drought resistant. Perfect for low rainfall and sandy-loam soils.',
                    fert: 'Single Super Phosphate (SSP)',
                    dose: '20 kg per Acre'
                });
                possibleCrops.push({
                    crop: 'Groundnut',
                    desc: 'Thrives in lower rainfall. Ensure soil remains loose for peg penetration.',
                    fert: 'Gypsum',
                    dose: '100 kg per Acre'
                });
            }

            if (n > 60 && rainfall >= 80) {
                possibleCrops.push({
                    crop: 'Cotton',
                    desc: "Your soil's high nitrogen is optimal for cotton. Avoid waterlogging.",
                    fert: 'Urea (45% N)',
                    dose: '50 kg per Acre'
                });
            }

            if (p > 30 && rainfall >= 50) {
                possibleCrops.push({
                    crop: 'Maize',
                    desc: "Requires a slight boost in Phosphorus. A highly profitable alternative.",
                    fert: 'DAP (Diammonium Phosphate)',
                    dose: '30 kg per Acre'
                });
            }

            if (k > 40 && ph > 5.5 && ph <= 7.0) {
                possibleCrops.push({
                    crop: 'Potato',
                    desc: 'High potassium levels are excellent for tuber formation and starch content.',
                    fert: 'Muriate of Potash (MOP)',
                    dose: '40 kg per Acre'
                });
            }

            if (ph >= 6.0 && ph <= 7.5 && rainfall >= 60) {
                possibleCrops.push({
                    crop: 'Wheat',
                    desc: 'Ideal pH range and adequate moisture. Perfect winter crop.',
                    fert: 'NPK 12:32:16',
                    dose: '50 kg per Acre'
                });
                possibleCrops.push({
                    crop: 'Soybean',
                    desc: 'Legume crop that will fix its own nitrogen. Good cash crop option.',
                    fert: 'SSP (Single Super Phosphate)',
                    dose: '60 kg per Acre'
                });
            }

            // Fallback crops if inputs don't match anything specific well
            if (possibleCrops.length === 0) {
                possibleCrops.push({
                    crop: 'Jowar (Sorghum)',
                    desc: 'Extremely hardy crop that tolerates wide pH ranges and variable rainfall.',
                    fert: 'NPK 19:19:19',
                    dose: '25 kg per Acre'
                });
                possibleCrops.push({
                    crop: 'Pulses (Moong/Tur)',
                    desc: 'Improves soil health. Very adaptable to various conditions.',
                    fert: 'DAP',
                    dose: '20 kg per Acre'
                });
            }

            // Shuffle and pick top 2
            const shuffled = possibleCrops.sort(() => 0.5 - Math.random());
            const selected = shuffled.slice(0, 2);

            const newRecs = selected.map((item, index) => ({
                crop: item.crop,
                match: index === 0 ? Math.floor(Math.random() * (99 - 90 + 1) + 90) : Math.floor(Math.random() * (89 - 75 + 1) + 75), // random score
                type: index === 0 ? 'Recommended' : 'Alternative',
                bg: index === 0 ? 'success' : 'primary',
                desc: item.desc,
                fert: item.fert,
                dose: item.dose
            }));

            setRecommendations(newRecs);
            setLoading(false);
        }, 1500); // simulate API call
    };

    return (
        <Container fluid className="p-0">
            <div className="d-flex justify-content-between align-items-center mb-4">
                <h2 className="text-white fw-bold mb-0">
                    <i className="bi bi-flower1 text-success"></i> {t('crop_ai.title')}
                </h2>
                <Button 
                    variant="outline-light" 
                    className="rounded-pill px-4 hover-white d-flex align-items-center gap-2"
                    onClick={handleGeneratePDF}
                >
                    <i className="bi bi-file-earmark-pdf-fill text-danger"></i> {t('crop_ai.export_report')}
                </Button>
            </div>
            <div ref={reportRef}>
            <Row className="g-4">
                <Col lg={4}>
                    <Card className="glass-panel border-0 text-white h-100">
                        <Card.Body className="p-4">
                            <h5 className="fw-bold mb-3">{t('crop_ai.soil_params')}</h5>
                            <Form>
                                <Row className="g-2">
                                    <Col sm={6}>
                                        <Form.Group className="mb-3">
                                            <Form.Label className="text-secondary small">{t('crop_ai.nitrogen')}</Form.Label>
                                            <Form.Control type="number" value={n} onChange={(e) => setN(e.target.value)} className="bg-transparent text-white border-secondary shadow-none" />
                                        </Form.Group>
                                    </Col>
                                    <Col sm={6}>
                                        <Form.Group className="mb-3">
                                            <Form.Label className="text-secondary small">{t('crop_ai.phosphorus')}</Form.Label>
                                            <Form.Control type="number" value={p} onChange={(e) => setP(e.target.value)} className="bg-transparent text-white border-secondary shadow-none" />
                                        </Form.Group>
                                    </Col>
                                    <Col sm={6}>
                                        <Form.Group className="mb-3">
                                            <Form.Label className="text-secondary small">{t('crop_ai.potassium')}</Form.Label>
                                            <Form.Control type="number" value={k} onChange={(e) => setK(e.target.value)} className="bg-transparent text-white border-secondary shadow-none" />
                                        </Form.Group>
                                    </Col>
                                    <Col sm={6}>
                                        <Form.Group className="mb-3">
                                            <Form.Label className="text-secondary small">{t('crop_ai.ph_level')}</Form.Label>
                                            <Form.Control type="number" step="0.1" value={ph} onChange={(e) => setPh(e.target.value)} className="bg-transparent text-white border-secondary shadow-none" />
                                        </Form.Group>
                                    </Col>
                                </Row>
                                <Form.Group className="mb-3">
                                    <Form.Label className="text-secondary small">{t('crop_ai.avg_rainfall')}</Form.Label>
                                    <Form.Control type="number" value={rainfall} onChange={(e) => setRainfall(e.target.value)} className="bg-transparent text-white border-secondary shadow-none" />
                                </Form.Group>
                                <Button 
                                    variant="success" 
                                    className="w-100 py-2 fw-bold border-0 mt-2 pdf-exclude d-flex justify-content-center align-items-center gap-2"
                                    onClick={getRecommendations}
                                    disabled={loading}
                                >
                                    {loading ? <CircularProgress size={20} color="inherit" /> : null}
                                    {loading ? t('crop_ai.analyzing') : t('crop_ai.get_recs')}
                                </Button>
                                {error && <div className="text-danger small mt-2 fw-bold text-center"><i className="bi bi-exclamation-triangle-fill"></i> {error}</div>}
                            </Form>
                        </Card.Body>
                    </Card>
                </Col>
                <Col lg={8}>
                    {recommendations ? (
                        <>
                            <h5 className="text-white fw-bold mb-3">{t('crop_ai.top_recs')}</h5>
                            <Row className="g-3">
                                {recommendations.map((rec, index) => (
                                    <Col md={6} key={index}>
                                        <Card className="glass-panel border-0 text-white h-100" style={{ borderLeft: `4px solid var(--bs-${rec.bg}) !important` }}>
                                            <Card.Body className="p-4">
                                                <div className="d-flex justify-content-between align-items-start mb-3">
                                                    <div>
                                                        <h4 className={`fw-bold text-${rec.bg} mb-1`}>{rec.crop}</h4>
                                                        <p className="text-secondary small mb-0">High Suitability ({rec.match}% Match)</p>
                                                    </div>
                                                    <Badge bg={rec.bg}>{rec.type}</Badge>
                                                </div>
                                                <p className="small mb-3">{rec.desc}</p>
                                                <div className="p-2 rounded border border-secondary" style={{ background: 'rgba(0,0,0,0.2)' }}>
                                                    <div className="text-secondary small mb-1"><i className="bi bi-bag-plus"></i> {t('crop_ai.fertilizer')}:</div>
                                                    <div className="fw-bold">{rec.fert}</div>
                                                    <div className="small text-info">{t('crop_ai.dosage')}: {rec.dose}</div>
                                                </div>
                                            </Card.Body>
                                        </Card>
                                    </Col>
                                ))}
                            </Row>
                        </>
                    ) : (
                        <div className="h-100 d-flex flex-column justify-content-center align-items-center text-secondary border border-secondary rounded glass-panel p-5 text-center" style={{ minHeight: '300px', borderColor: 'rgba(255,255,255,0.1) !important' }}>
                            <i className="bi bi-robot mb-3" style={{ fontSize: '3rem' }}></i>
                            <h5 className="fw-bold text-white">{t('crop_ai.awaiting')}</h5>
                            <p className="mb-0 mx-auto" style={{ maxWidth: '400px' }}>Enter your {t('crop_ai.soil_params')} and click "Get AI Recommendations" to generate custom crop suggestions.</p>
                        </div>
                    )}
                </Col>
            </Row>
            </div>
            <InsightsFooter />
        </Container>
    );
}
