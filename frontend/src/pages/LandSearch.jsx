import React, { useState, useContext, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { Container, Row, Col, Card, Form, InputGroup, Button, Badge, Spinner, Alert } from 'react-bootstrap';
import { SavedSearchContext } from '../context/SavedSearchContext';
import { useTranslation } from 'react-i18next';
import { landApi } from '../api/landApi';
import { extractErrorMessage } from '../api/client';

/**
 * Derives the badge list the card renders from real listing attributes.
 *
 * The mock data carried a hand-written `tags` array. Real listings do not have one, so the badges
 * are computed from facts the backend actually knows — which also means they cannot lie: a
 * "Verified" badge now appears only when an administrator has verified the listing.
 */
function deriveTags(land) {
    const tags = [];
    if (land.verified) tags.push('Verified');
    if (land.landIntelligenceScore >= 85) tags.push('Premium');
    else if (land.landIntelligenceScore >= 75) tags.push('High Yield');
    if (land.landIntelligenceScore < 55) tags.push('Investment');
    return tags;
}

/** Maps the API response onto the field names this page's markup already uses. */
function toViewModel(land) {
    const depth = land.groundwaterLevelDepth;
    let water = 'Unknown';
    if (depth != null) {
        const band = depth <= 30 ? 'Excellent' : depth <= 80 ? 'High' : depth <= 150 ? 'Moderate' : 'Low';
        water = `${band} (${depth}m)`;
    }
    return {
        id: land.id,
        title: land.title,
        location: land.location,
        district: land.district,
        size: land.sizeInAcres,
        price: land.price,
        score: land.landIntelligenceScore,
        soil: land.soilType,
        borewell: land.borewellSuccessProbability,
        water,
        tags: deriveTags(land),
    };
}


export default function LandSearch() {
    const [searchTerm, setSearchTerm] = useState('');
    const [filterCity, setFilterCity] = useState('All');
    const [soilType, setSoilType] = useState('All');
    const [maxPrice, setMaxPrice] = useState(10000000);
    const [minScore, setMinScore] = useState(0);
    const [verifiedOnly, setVerifiedOnly] = useState(false);
    const [showAdvanced, setShowAdvanced] = useState(false);

    const [lands, setLands] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [totalResults, setTotalResults] = useState(0);
    const navigate = useNavigate();
    const { addSavedSearch } = useContext(SavedSearchContext);
    const { t } = useTranslation();

    const fetchLands = useCallback(async (signal) => {
        setLoading(true);
        setError(null);
        try {
            const { data } = await landApi.search({
                q: searchTerm || undefined,
                district: filterCity === 'All' ? undefined : filterCity,
                soilType: soilType === 'All' ? undefined : soilType,
                maxPrice: maxPrice < 10000000 ? maxPrice : undefined,
                minScore: minScore > 0 ? minScore : undefined,
                verified: verifiedOnly ? true : undefined,
            }, 0, 24);
            if (signal?.aborted) return;
            setLands((data.content || []).map(toViewModel));
            setTotalResults(data.totalElements ?? 0);
        } catch (err) {
            if (signal?.aborted) return;
            setError(extractErrorMessage(err, 'Could not load listings.'));
            setLands([]);
            setTotalResults(0);
        } finally {
            if (!signal?.aborted) setLoading(false);
        }
    }, [searchTerm, filterCity, soilType, maxPrice, minScore, verifiedOnly]);

    // Debounced so typing in the search box does not fire a request per keystroke.
    useEffect(() => {
        const controller = new AbortController();
        const timer = setTimeout(() => fetchLands(controller.signal), 350);
        return () => {
            clearTimeout(timer);
            controller.abort();
        };
    }, [fetchLands]);

    const handleViewDetails = (land) => {
        navigate(`/buyer/analysis?landId=${land.id}`);
    };

    const handleSaveProperty = async (land) => {
        const result = await addSavedSearch({
            name: land.title,
            pin: land.location,
            soil: land.soil,
            landId: land.id,
        });
        // Report what actually happened rather than assuming success, which is what the previous
        // localStorage-only version could get away with.
        if (result?.alreadySaved) {
            alert('This property is already in your shortlist.');
        } else if (result?.success) {
            alert(t('land_search.prop_saved'));
        } else {
            alert(result?.message || 'Could not save this property.');
        }
    };

    const handleAddToCompare = (land) => {
        const stored = sessionStorage.getItem('compareList') || localStorage.getItem('compareList');
        let compareList = stored ? JSON.parse(stored) : [];
        if (!compareList.find(item => String(item.id) === String(land.id))) {
            if (compareList.length >= 3) {
                alert(t('land_search.compare_limit'));
                return;
            }
            compareList.push(land);
            sessionStorage.setItem('compareList', JSON.stringify(compareList));
            localStorage.setItem('compareList', JSON.stringify(compareList));
        }
        navigate('/buyer/compare');
    };

    // Filtering now happens in SQL via the specification API, so the client just renders results.
    const filteredLands = lands;

    const formatPrice = (price) => {
        return `₹${(price / 100000).toFixed(1)} Lakhs`;
    };

    const handleCityChange = (e) => {
        const selectedRegion = e.target.value;
        setFilterCity(selectedRegion);
        if (selectedRegion === 'All') {
            setSearchTerm('');
        } else {
            setSearchTerm(selectedRegion);
        }
    };

    const handleSearchTermChange = (e) => {
        const text = e.target.value;
        setSearchTerm(text);
        if (!text.trim()) {
            setFilterCity('All');
        }
    };

    const resetFilters = () => {
        setSearchTerm('');
        setFilterCity('All');
        setSoilType('All');
        setMaxPrice(10000000);
        setMinScore(0);
        setVerifiedOnly(false);
    };

    return (
        <Container fluid className="p-0">
            <h2 className="text-white fw-bold mb-4">
                <i className="bi bi-search text-primary"></i> Smart Land Search
            </h2>

            {/* Search and Filter Dashboard Bar */}
            <Card className="glass-panel border-0 mb-4 text-white">
                <Card.Body className="p-4">
                    <Row className="g-3 align-items-center">
                        <Col lg={6}>
                            <InputGroup>
                                <InputGroup.Text className="bg-transparent border-secondary text-secondary">
                                    <i className="bi bi-geo-alt-fill"></i>
                                </InputGroup.Text>
                                <Form.Control
                                    type="text"
                                    placeholder="Search by city, area, or property title..."
                                    value={searchTerm}
                                    onChange={handleSearchTermChange}
                                    className="bg-transparent text-white border-secondary shadow-none"
                                />
                            </InputGroup>
                        </Col>
                        <Col lg={3}>
                            <Form.Select 
                                value={filterCity}
                                onChange={handleCityChange}
                                className="bg-transparent text-white border-secondary shadow-none"
                            >
                                <option value="All" className="bg-dark">All Regions</option>
                                <option value="Ahmednagar" className="bg-dark">Ahmednagar</option>
                                <option value="Akola" className="bg-dark">Akola</option>
                                <option value="Amravati" className="bg-dark">Amravati</option>
                                <option value="Aurangabad" className="bg-dark">Aurangabad (Chhatrapati Sambhajinagar)</option>
                                <option value="Baramati" className="bg-dark">Baramati</option>
                                <option value="Bhusawal" className="bg-dark">Bhusawal</option>
                                <option value="Chandrapur" className="bg-dark">Chandrapur</option>
                                <option value="Dhule" className="bg-dark">Dhule</option>
                                <option value="Gondia" className="bg-dark">Gondia</option>
                                <option value="Hingoli" className="bg-dark">Hingoli</option>
                                <option value="Jalgaon" className="bg-dark">Jalgaon</option>
                                <option value="Jalna" className="bg-dark">Jalna</option>
                                <option value="Kolhapur" className="bg-dark">Kolhapur</option>
                                <option value="Latur" className="bg-dark">Latur</option>
                                <option value="Malegaon" className="bg-dark">Malegaon</option>
                                <option value="Mumbai" className="bg-dark">Mumbai</option>
                                <option value="Nagpur" className="bg-dark">Nagpur</option>
                                <option value="Nanded" className="bg-dark">Nanded</option>
                                <option value="Nandurbar" className="bg-dark">Nandurbar</option>
                                <option value="Nashik" className="bg-dark">Nashik</option>
                                <option value="Osmanabad" className="bg-dark">Osmanabad (Dharashiv)</option>
                                <option value="Parbhani" className="bg-dark">Parbhani</option>
                                <option value="Pune" className="bg-dark">Pune</option>
                                <option value="Raigad" className="bg-dark">Raigad</option>
                                <option value="Ratnagiri" className="bg-dark">Ratnagiri</option>
                                <option value="Sangli" className="bg-dark">Sangli</option>
                                <option value="Satara" className="bg-dark">Satara</option>
                                <option value="Sindhudurg" className="bg-dark">Sindhudurg</option>
                                <option value="Solapur" className="bg-dark">Solapur</option>
                                <option value="Thane" className="bg-dark">Thane</option>
                                <option value="Wardha" className="bg-dark">Wardha</option>
                                <option value="Washim" className="bg-dark">Washim</option>
                                <option value="Yavatmal" className="bg-dark">Yavatmal</option>
                            </Form.Select>
                        </Col>
                        <Col lg={3}>
                            <Button 
                                variant={showAdvanced ? "success" : "primary"} 
                                className="w-100 rounded-pill fw-bold"
                                onClick={() => setShowAdvanced(!showAdvanced)}
                            >
                                <i className={`bi bi-${showAdvanced ? 'x-circle-fill' : 'funnel-fill'} me-2`}></i> 
                                {showAdvanced ? 'Hide Filters' : 'Advanced Filters'}
                            </Button>
                        </Col>
                    </Row>

                    {showAdvanced && (
                        <div className="mt-4 pt-3 border-top border-secondary">
                            <Row className="g-3 align-items-end">
                                <Col md={3}>
                                    <Form.Label className="small text-secondary fw-bold">Soil Type</Form.Label>
                                    <Form.Select 
                                        value={soilType}
                                        onChange={(e) => setSoilType(e.target.value)}
                                        className="bg-transparent text-white border-secondary shadow-none"
                                    >
                                        <option value="All" className="bg-dark">All Soils</option>
                                        <option value="Black Cotton" className="bg-dark">Black Cotton Soil</option>
                                        <option value="Red Loam" className="bg-dark">Red Loam Soil</option>
                                        <option value="Alluvial" className="bg-dark">Alluvial Soil</option>
                                        <option value="Laterite" className="bg-dark">Laterite Soil</option>
                                        <option value="Clay Loam" className="bg-dark">Clay Loam Soil</option>
                                    </Form.Select>
                                </Col>
                                <Col md={3}>
                                    <Form.Label className="small text-secondary fw-bold">
                                        Max Budget: {maxPrice >= 10000000 ? 'Any' : `₹${(maxPrice / 100000).toFixed(1)} Lakhs`}
                                    </Form.Label>
                                    <Form.Range 
                                        min={1000000}
                                        max={10000000}
                                        step={500000}
                                        value={maxPrice}
                                        onChange={(e) => setMaxPrice(Number(e.target.value))}
                                    />
                                </Col>
                                <Col md={3}>
                                    <Form.Label className="small text-secondary fw-bold">
                                        Min Intelligence Score: {minScore > 0 ? `${minScore}+` : 'Any'}
                                    </Form.Label>
                                    <Form.Range 
                                        min={0}
                                        max={95}
                                        step={5}
                                        value={minScore}
                                        onChange={(e) => setMinScore(Number(e.target.value))}
                                    />
                                </Col>
                                <Col md={3} className="d-flex align-items-center justify-content-between pb-1">
                                    <Form.Check 
                                        type="switch"
                                        id="verified-switch"
                                        label="Verified Only"
                                        checked={verifiedOnly}
                                        onChange={(e) => setVerifiedOnly(e.target.checked)}
                                        className="text-white"
                                    />
                                    <Button variant="link" className="text-secondary text-decoration-none p-0 small" onClick={resetFilters}>
                                        <i className="bi bi-arrow-counterclockwise"></i> Reset All
                                    </Button>
                                </Col>
                            </Row>
                        </div>
                    )}
                </Card.Body>
            </Card>

            {/* Results Info */}
            {error && (
                <Alert variant="danger" className="mb-3">
                    {error}{' '}
                    <Button variant="link" size="sm" className="p-0 align-baseline" onClick={() => fetchLands()}>
                        Retry
                    </Button>
                </Alert>
            )}

            <div className="mb-3 d-flex justify-content-between align-items-center">
                <h5 className="text-secondary mb-0">
                    {loading ? (
                        <><Spinner animation="border" size="sm" className="me-2" /> Searching…</>
                    ) : (
                        <>Found <span className="text-white fw-bold">{totalResults}</span> properties</>
                    )}
                </h5>
            </div>

            {!loading && !error && filteredLands.length === 0 && (
                <Card className="glass-panel border-0 text-white text-center p-5 mb-4">
                    <i className="bi bi-search text-secondary" style={{ fontSize: '2.5rem', opacity: 0.5 }}></i>
                    <p className="text-secondary mt-3 mb-0">
                        No listings match your search. Try widening the region or clearing the search box.
                    </p>
                </Card>
            )}

            {/* Land Cards Grid */}
            <Row className="g-4">
                {filteredLands.map(land => (
                    <Col xl={4} lg={6} key={land.id}>
                        <Card className="glass-panel border-0 text-white h-100 hover-scale" style={{ transition: 'transform 0.2s' }}>
                            {/* Card Image Placeholder */}
                            <div 
                                style={{ 
                                    height: '200px', 
                                    background: 'linear-gradient(135deg, rgba(41, 121, 255, 0.2), rgba(0, 230, 118, 0.2))',
                                    borderTopLeftRadius: '16px',
                                    borderTopRightRadius: '16px',
                                    position: 'relative'
                                }}
                                className="d-flex align-items-center justify-content-center"
                            >
                                <Button 
                                    variant="light" 
                                    className="rounded-circle shadow border-0" 
                                    style={{ 
                                        position: 'absolute', 
                                        top: '12px', 
                                        left: '12px', 
                                        width: '38px', 
                                        height: '38px', 
                                        padding: 0, 
                                        display: 'flex', 
                                        alignItems: 'center', 
                                        justifyContent: 'center',
                                        zIndex: 10,
                                        cursor: 'pointer'
                                    }}
                                    title="Save Property"
                                    onClick={(e) => {
                                        e.stopPropagation();
                                        handleSaveProperty(land);
                                    }}
                                >
                                    <i className="bi bi-bookmark-fill text-primary fs-6"></i>
                                </Button>
                                <i className="bi bi-image text-secondary" style={{ fontSize: '3rem', opacity: 0.5 }}></i>
                                <div style={{ position: 'absolute', top: '12px', right: '12px', display: 'flex', gap: '6px', zIndex: 5, flexWrap: 'wrap', justifyContent: 'flex-end' }}>
                                    {land.tags.map((tag, idx) => (
                                        <Badge bg={tag === 'Verified' ? 'success' : 'primary'} key={idx} className="shadow-sm px-2 py-1">
                                            {tag === 'Verified' && <i className="bi bi-patch-check-fill me-1"></i>}
                                            {tag}
                                        </Badge>
                                    ))}
                                </div>
                            </div>

                            <Card.Body className="p-4 d-flex flex-column">
                                <div className="d-flex justify-content-between align-items-start mb-2">
                                    <h5 className="fw-bold text-gradient mb-0">{land.title}</h5>
                                </div>
                                <p className="text-secondary mb-3"><i className="bi bi-geo-alt text-danger me-1"></i> {land.location}</p>

                                <div className="d-flex justify-content-between align-items-center mb-4 p-3 rounded" style={{ background: 'rgba(0,0,0,0.2)', border: '1px solid rgba(255,255,255,0.05)' }}>
                                    <div>
                                        <p className="text-secondary small mb-0">Total Price</p>
                                        <h4 className="fw-bold mb-0 text-white">{formatPrice(land.price)}</h4>
                                    </div>
                                    <div className="text-end">
                                        <p className="text-secondary small mb-0">Size</p>
                                        <h5 className="fw-bold mb-0 text-info">{land.size} Acres</h5>
                                    </div>
                                </div>

                                <Row className="mb-4 flex-grow-1">
                                    <Col xs={6} className="mb-3">
                                        <p className="text-secondary small mb-1"><i className="bi bi-layers-fill text-warning me-1"></i> Soil Type</p>
                                        <span className="fw-bold">{land.soil}</span>
                                    </Col>
                                    <Col xs={6} className="mb-3">
                                        <p className="text-secondary small mb-1"><i className="bi bi-droplet-fill text-primary me-1"></i> Water</p>
                                        <span className="fw-bold">{land.water}</span>
                                    </Col>
                                    <Col xs={12}>
                                        <div className="d-flex align-items-center justify-content-between">
                                            <span className="text-secondary small">EarthScan Intelligence Score</span>
                                            <Badge bg={land.score >= 80 ? 'success' : land.score >= 60 ? 'warning' : 'danger'}>
                                                {land.score}/100
                                            </Badge>
                                        </div>
                                        <div className="progress mt-2" style={{ height: '6px', background: 'rgba(255,255,255,0.1)' }}>
                                            <div 
                                                className={`progress-bar ${land.score >= 80 ? 'bg-success' : land.score >= 60 ? 'bg-warning' : 'bg-danger'}`} 
                                                role="progressbar" 
                                                style={{ width: `${land.score}%` }}
                                            ></div>
                                        </div>
                                    </Col>
                                </Row>

                                <div className="d-flex gap-2 mt-auto">
                                    <Button variant="outline-light" className="w-50 rounded-pill hover-white" onClick={() => handleViewDetails(land)}>
                                        View Details
                                    </Button>
                                    <Button variant="primary" className="w-50 rounded-pill fw-bold" onClick={() => handleAddToCompare(land)}>
                                        Add to Compare
                                    </Button>
                                </div>
                            </Card.Body>
                        </Card>
                    </Col>
                ))}
                
                {filteredLands.length === 0 && (
                    <Col xs={12}>
                        <div className="text-center p-5 text-secondary glass-panel rounded-4">
                            <i className="bi bi-search mb-3 d-block" style={{ fontSize: '3rem' }}></i>
                            <h5>No properties found</h5>
                            <p>Try adjusting your search terms or filters to find more properties.</p>
                        </div>
                    </Col>
                )}
            </Row>
        </Container>
    );
}
