import React, { useState } from 'react';
import { Container, Row, Col, Card, Table, Badge, Button, Form, InputGroup } from 'react-bootstrap';
import InsightsFooter from '../components/InsightsFooter';
import { useTranslation } from 'react-i18next';

export default function MandiSchemes() {
    const [searchQuery, setSearchQuery] = useState('');
    const { t } = useTranslation();

    const mandiData = [
        { id: 1, commodity: 'Wheat', market: 'Pune APMC', minPrice: '₹2,100/q', modalPrice: '₹2,350/q', trendText: '+2.5%', isUp: true },
        { id: 2, commodity: 'Cotton', market: 'Jalgaon', minPrice: '₹6,800/q', modalPrice: '₹7,200/q', trendText: '+1.2%', isUp: true },
        { id: 3, commodity: 'Soyabean', market: 'Latur', minPrice: '₹4,200/q', modalPrice: '₹4,450/q', trendText: '-0.8%', isUp: false },
        { id: 4, commodity: 'Onion', market: 'Lasalgaon', minPrice: '₹1,500/q', modalPrice: '₹2,100/q', trendText: '+5.4%', isUp: true },
        { id: 5, commodity: 'Tomato', market: 'Nashik', minPrice: '₹800/q', modalPrice: '₹1,200/q', trendText: '+1.5%', isUp: true },
        { id: 6, commodity: 'Maize', market: 'Aurangabad', minPrice: '₹1,800/q', modalPrice: '₹1,950/q', trendText: '-0.2%', isUp: false }
    ];

    const filteredData = mandiData.filter(item => 
        item.market.toLowerCase().includes(searchQuery.toLowerCase()) || 
        item.commodity.toLowerCase().includes(searchQuery.toLowerCase())
    );

    return (
        <Container fluid className="p-0">
            <h2 className="text-white fw-bold mb-4">
                <i className="bi bi-graph-up-arrow text-warning"></i> {t('mandi.title')}
            </h2>
            <Row className="g-4">
                {/* Live Mandi Prices Table */}
                <Col lg={7}>
                    <Card className="glass-panel border-0 text-white h-100">
                        <Card.Body className="p-4">
                            <div className="d-flex justify-content-between align-items-center mb-4">
                                <h5 className="fw-bold mb-0">{t('mandi.prices_tab')}</h5>
                                <Badge bg="danger" className="d-flex align-items-center gap-1">
                                    <span className="spinner-grow spinner-grow-sm" role="status" aria-hidden="true"></span>
                                    {t('mandi.live_badge')}
                                </Badge>
                            </div>

                            <Form className="mb-4">
                                <InputGroup>
                                    <InputGroup.Text className="bg-transparent border-secondary text-secondary">
                                        <i className="bi bi-search"></i>
                                    </InputGroup.Text>
                                    <Form.Control
                                        type="text"
                                        placeholder={t('common.search')}
                                        className="bg-transparent text-white border-secondary shadow-none"
                                        value={searchQuery}
                                        onChange={(e) => setSearchQuery(e.target.value)}
                                    />
                                </InputGroup>
                            </Form>

                            <Table variant="dark" hover responsive className="bg-transparent">
                                <thead>
                                    <tr>
                                        <th className="text-secondary bg-transparent border-secondary">{t('mandi.table_commodity')}</th>
                                        <th className="text-secondary bg-transparent border-secondary">{t('mandi.table_market')}</th>
                                        <th className="text-secondary bg-transparent border-secondary">{t('mandi.table_min_price')}</th>
                                        <th className="text-secondary bg-transparent border-secondary">{t('mandi.table_modal_price')}</th>
                                        <th className="text-secondary bg-transparent border-secondary">{t('mandi.table_trend')}</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {filteredData.length > 0 ? (
                                        filteredData.map(item => (
                                            <tr key={item.id}>
                                                <td className="bg-transparent border-secondary">{item.commodity}</td>
                                                <td className="bg-transparent border-secondary">{item.market}</td>
                                                <td className="bg-transparent border-secondary">{item.minPrice}</td>
                                                <td className={`bg-transparent border-secondary fw-bold text-${item.isUp ? 'success' : 'danger'}`}>
                                                    {item.modalPrice}
                                                </td>
                                                <td className={`bg-transparent border-secondary text-${item.isUp ? 'success' : 'danger'}`}>
                                                    <i className={`bi bi-arrow-${item.isUp ? 'up' : 'down'}-right`}></i> {item.trendText}
                                                </td>
                                            </tr>
                                        ))
                                    ) : (
                                        <tr>
                                            <td colSpan="5" className="text-center bg-transparent border-secondary py-4 text-secondary">
                                                {t('mandi.no_results')} "{searchQuery}"
                                            </td>
                                        </tr>
                                    )}
                                </tbody>
                            </Table>
                        </Card.Body>
                    </Card>
                </Col>

                {/* Government Schemes Panel */}
                <Col lg={5}>
                    <Card className="glass-panel border-0 text-white h-100">
                        <Card.Body className="p-4">
                            <h5 className="fw-bold mb-4">{t('mandi.schemes_heading')}</h5>
                            
                            <div className="d-flex flex-column gap-3">
                                {/* Scheme 1: PM-Kisan */}
                                <div className="p-3 rounded border border-secondary" style={{ background: 'rgba(0,0,0,0.2)' }}>
                                    <div className="d-flex justify-content-between align-items-start mb-2">
                                        <h6 className="fw-bold mb-0 text-warning">{t('mandi.scheme1_name')}</h6>
                                        <Badge bg="success">{t('mandi.active_badge')}</Badge>
                                    </div>
                                    <p className="text-secondary small mb-3">{t('mandi.scheme1_desc')}</p>
                                    <Button 
                                        as="a" 
                                        href="https://pmkisan.gov.in/" 
                                        target="_blank" 
                                        rel="noopener noreferrer"
                                        variant="outline-light" 
                                        size="sm" 
                                        className="w-100 rounded-pill border-secondary hover-white"
                                    >
                                        {t('mandi.scheme1_btn')} <i className="bi bi-box-arrow-up-right ms-1"></i>
                                    </Button>
                                </div>

                                {/* Scheme 2: PM Fasal Bima */}
                                <div className="p-3 rounded border border-secondary" style={{ background: 'rgba(0,0,0,0.2)' }}>
                                    <div className="d-flex justify-content-between align-items-start mb-2">
                                        <h6 className="fw-bold mb-0 text-info">{t('mandi.scheme2_name')}</h6>
                                        <Badge bg="success">{t('mandi.active_badge')}</Badge>
                                    </div>
                                    <p className="text-secondary small mb-3">{t('mandi.scheme2_desc')}</p>
                                    <Button 
                                        as="a" 
                                        href="https://pmfby.gov.in/" 
                                        target="_blank" 
                                        rel="noopener noreferrer"
                                        variant="outline-light" 
                                        size="sm" 
                                        className="w-100 rounded-pill border-secondary hover-white"
                                    >
                                        {t('mandi.scheme2_btn')} <i className="bi bi-box-arrow-up-right ms-1"></i>
                                    </Button>
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
