import React, { useState, useEffect, useMemo } from 'react';
import { Container, Row, Col, Card, Form, Button, Badge } from 'react-bootstrap';
import { useSearchParams } from 'react-router-dom';
import { Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, AreaChart, Area } from 'recharts';
import InsightsFooter from '../components/InsightsFooter';
import { CircularProgress } from '@mui/material';
import { useTranslation } from 'react-i18next';

const DEFAULT_REGIONS = ['Pune', 'Nashik', 'Nagpur', 'Ratnagiri', 'Jalgaon', 'Latur', 'Aurangabad', 'Kolhapur', 'Solapur'];

export default function InvestmentAnalysis() {
    const [searchParams] = useSearchParams();
    const queryRegion = searchParams.get('region') || searchParams.get('city') || 'Pune';

    const [region, setRegion] = useState(queryRegion);
    const [crop, setCrop] = useState('Sugarcane');
    const [investment, setInvestment] = useState(5000000);
    const [years, setYears] = useState(5);
    
    const [loading, setLoading] = useState(false);
    const [results, setResults] = useState(null);
    const { t } = useTranslation();

    const regionOptions = useMemo(() => {
        if (region && !DEFAULT_REGIONS.includes(region)) {
            return [region, ...DEFAULT_REGIONS];
        }
        return DEFAULT_REGIONS;
    }, [region]);

    const getSeededFactor = (targetReg, targetCrop, invVal, yearIndex) => {
        const seedStr = `${targetReg}_${targetCrop}_${invVal}_year${yearIndex}`;
        let hash = 0;
        for (let i = 0; i < seedStr.length; i++) {
            hash = (hash << 5) - hash + seedStr.charCodeAt(i);
            hash |= 0;
        }
        return (Math.abs(hash % 1000) / 1000) * 0.03;
    };

    const runSimulation = (targetRegionOverride) => {
        const targetRegion = targetRegionOverride || region;
        setLoading(true);
        setTimeout(() => {
            // Generate deterministic projection data based on input parameters
            const data = [];
            let currentVal = Number(investment);
            const baseGrowthRate = crop === 'Sugarcane' ? 0.12 : crop === 'Cotton' ? 0.08 : crop === 'Mango' ? 0.15 : 0.10;
            
            for (let i = 0; i <= years; i++) {
                data.push({
                    year: `Year ${i}`,
                    value: Math.round(currentVal),
                    cost: Math.round(Number(investment) + (i * 200000)) // Assuming 2L maintenance per year
                });
                // Compound growth with deterministic regional market factor
                const factor = getSeededFactor(targetRegion, crop, investment, i);
                currentVal += (currentVal * (baseGrowthRate + factor));
            }

            const finalValue = data[data.length - 1].value;
            const totalCost = data[data.length - 1].cost;
            const roi = (((finalValue - totalCost) / totalCost) * 100).toFixed(1);

            setResults({
                region: targetRegion,
                data,
                finalValue,
                roi,
                breakEven: crop === 'Mango' ? 'Year 4' : 'Year 2',
                risk: crop === 'Cotton' ? 'High' : 'Medium'
            });
            setLoading(false);
        }, 1000);
    };

    useEffect(() => {
        const reg = searchParams.get('region') || searchParams.get('city');
        if (reg) {
            setRegion(reg);
            runSimulation(reg);
        } else {
            runSimulation(region);
        }
    }, [searchParams]);

    const handleSimulate = () => {
        runSimulation(region);
    };

    const formatCurrency = (val) => {
        return `₹${(val / 100000).toFixed(1)}L`;
    };

    return (
        <Container fluid className="p-0">
            <h2 className="text-white fw-bold mb-4">
                <i className="bi bi-graph-up-arrow text-success"></i> {t('investment.title')}
            </h2>

            <Row className="g-4 mb-4">
                <Col lg={4}>
                    <Card className="glass-panel border-0 text-white h-100">
                        <Card.Body className="p-4">
                            <h5 className="fw-bold mb-4">Simulation Parameters</h5>
                            <Form>
                                <Form.Group className="mb-3">
                                    <Form.Label className="text-secondary small">Target Region</Form.Label>
                                    <Form.Select 
                                        value={region} 
                                        onChange={e => {
                                            const newReg = e.target.value;
                                            setRegion(newReg);
                                            runSimulation(newReg);
                                        }}
                                        className="bg-transparent text-white border-secondary shadow-none"
                                    >
                                        {regionOptions.map(r => (
                                            <option key={r} value={r} className="bg-dark">{r}</option>
                                        ))}
                                    </Form.Select>
                                </Form.Group>

                                <Form.Group className="mb-3">
                                    <Form.Label className="text-secondary small">Primary Planned Crop</Form.Label>
                                    <Form.Select 
                                        value={crop} 
                                        onChange={e => setCrop(e.target.value)}
                                        className="bg-transparent text-white border-secondary shadow-none"
                                    >
                                        <option value="Sugarcane" className="bg-dark">Sugarcane</option>
                                        <option value="Cotton" className="bg-dark">Cotton</option>
                                        <option value="Mango" className="bg-dark">Alphonso Mango</option>
                                        <option value="Soybean" className="bg-dark">Soybean</option>
                                    </Form.Select>
                                </Form.Group>

                                <Form.Group className="mb-3">
                                    <Form.Label className="text-secondary small">Initial Investment (₹)</Form.Label>
                                    <Form.Control 
                                        type="number" 
                                        value={investment} 
                                        onChange={e => setInvestment(e.target.value)}
                                        className="bg-transparent text-white border-secondary shadow-none"
                                    />
                                </Form.Group>

                                <Form.Group className="mb-4">
                                    <Form.Label className="text-secondary small">Time Horizon (Years): {years}</Form.Label>
                                    <Form.Range 
                                        min={1} max={15} 
                                        value={years} 
                                        onChange={e => setYears(e.target.value)} 
                                    />
                                </Form.Group>

                                <Button 
                                    variant="success" 
                                    className="w-100 py-2 fw-bold rounded-pill d-flex justify-content-center align-items-center gap-2"
                                    onClick={handleSimulate}
                                    disabled={loading}
                                >
                                    {loading ? <CircularProgress size={20} color="inherit" /> : <i className="bi bi-cpu"></i>}
                                    {loading ? 'Running AI Model...' : 'Run Simulation'}
                                </Button>
                            </Form>
                        </Card.Body>
                    </Card>
                </Col>

                <Col lg={8}>
                    {results ? (
                        <>
                            <Row className="g-3 mb-4">
                                <Col md={4}>
                                    <Card className="glass-panel border-0 text-white text-center">
                                        <Card.Body>
                                            <h6 className="text-secondary mb-2">Projected ROI</h6>
                                            <h2 className="fw-bold text-success mb-0">+{results.roi}%</h2>
                                        </Card.Body>
                                    </Card>
                                </Col>
                                <Col md={4}>
                                    <Card className="glass-panel border-0 text-white text-center">
                                        <Card.Body>
                                            <h6 className="text-secondary mb-2">Estimated Value (Yr {years})</h6>
                                            <h2 className="fw-bold text-info mb-0">{formatCurrency(results.finalValue)}</h2>
                                        </Card.Body>
                                    </Card>
                                </Col>
                                <Col md={4}>
                                    <Card className="glass-panel border-0 text-white text-center">
                                        <Card.Body>
                                            <h6 className="text-secondary mb-2">Break-Even Point</h6>
                                            <h2 className="fw-bold text-warning mb-0">{results.breakEven}</h2>
                                        </Card.Body>
                                    </Card>
                                </Col>
                            </Row>

                            <Card className="glass-panel border-0 text-white">
                                <Card.Body className="p-4">
                                    <div className="d-flex justify-content-between align-items-center mb-4">
                                        <h5 className="fw-bold mb-0">Value Growth Projection</h5>
                                        <Badge bg={results.risk === 'High' ? 'danger' : 'warning'}>Risk: {results.risk}</Badge>
                                    </div>
                                    <div style={{ height: '300px', width: '100%' }}>
                                        <ResponsiveContainer width="100%" height="100%">
                                            <AreaChart data={results.data} margin={{ top: 10, right: 30, left: 0, bottom: 0 }}>
                                                <defs>
                                                    <linearGradient id="colorValue" x1="0" y1="0" x2="0" y2="1">
                                                        <stop offset="5%" stopColor="#00e676" stopOpacity={0.8}/>
                                                        <stop offset="95%" stopColor="#00e676" stopOpacity={0}/>
                                                    </linearGradient>
                                                </defs>
                                                <XAxis dataKey="year" stroke="#a0aec0" />
                                                <YAxis stroke="#a0aec0" tickFormatter={formatCurrency} />
                                                <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.1)" />
                                                <Tooltip 
                                                    contentStyle={{ backgroundColor: '#0a0f18', borderColor: '#2979ff', color: '#fff' }}
                                                    itemStyle={{ color: '#00e676' }}
                                                    formatter={(value) => formatCurrency(value)}
                                                />
                                                <Area type="monotone" dataKey="value" stroke="#00e676" fillOpacity={1} fill="url(#colorValue)" name="Projected Value" />
                                                <Line type="monotone" dataKey="cost" stroke="#ff5252" strokeWidth={2} dot={false} name="Cumulative Cost" />
                                            </AreaChart>
                                        </ResponsiveContainer>
                                    </div>
                                </Card.Body>
                            </Card>
                        </>
                    ) : (
                        <Card className="glass-panel border-0 text-white h-100 d-flex justify-content-center align-items-center">
                            <Card.Body className="text-center p-5 text-secondary">
                                <i className="bi bi-bar-chart-line mb-3 d-block" style={{ fontSize: '4rem', opacity: 0.5 }}></i>
                                <h4 className="fw-bold text-white mb-2">Awaiting Parameters</h4>
                                <p className="mb-0 mx-auto" style={{ maxWidth: '400px' }}>Adjust the parameters on the left and run the simulation to see projected ROI, land appreciation, and yield revenue over time.</p>
                            </Card.Body>
                        </Card>
                    )}
                </Col>
            </Row>

            <InsightsFooter />
        </Container>
    );
}
