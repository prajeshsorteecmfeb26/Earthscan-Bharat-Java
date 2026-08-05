import React, { useContext } from 'react';
import { Card, Row, Col, Button } from 'react-bootstrap';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { AuthContext } from '../context/AuthContext';
import { normalizeRole } from '../utils/roleUtils';

export default function InsightsFooter({ showContact }) {
    const { t } = useTranslation();
    const { user } = useContext(AuthContext) || {};
    const role = normalizeRole(user?.role || user?.Role);
    const isAdmin = role === 'Admin';

    const shouldShowContact = showContact !== undefined ? showContact : !isAdmin;

    return (
        <Card className="glass-panel border-0 text-white mt-5" style={{ background: 'rgba(10, 15, 24, 0.6)', borderTop: '2px solid rgba(255,255,255,0.05) !important' }}>
            <Card.Body className="p-4">
                <Row className="align-items-center g-4">
                    <Col md={shouldShowContact ? 8 : 12}>
                        <h5 className="fw-bold text-success mb-2">
                            <i className="bi bi-lightbulb-fill text-warning"></i> EarthScan Agri-Tech Insights
                        </h5>
                        <p className="text-secondary small mb-0">
                            {t('common.insights_tip')}
                        </p>
                    </Col>
                    {shouldShowContact && (
                        <Col md={4} className="text-md-end">
                            <Button as={Link} to="/contact" variant="outline-light" className="rounded-pill px-4 hover-white fw-bold">
                                {t('common.contact_support')}
                            </Button>
                        </Col>
                    )}
                </Row>
            </Card.Body>
        </Card>
    );
}

