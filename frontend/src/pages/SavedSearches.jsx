import React, { useContext } from 'react';
import { Container, Row, Col, Card, Button } from 'react-bootstrap';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import InsightsFooter from '../components/InsightsFooter';
import { SavedSearchContext } from '../context/SavedSearchContext';

export default function SavedSearches() {
    const { savedLocations, removeSavedSearch } = useContext(SavedSearchContext);
    const { t } = useTranslation();
    const navigate = useNavigate();

    const handleDelete = (id) => {
        removeSavedSearch(id);
    };

    const extractCityFromLocation = (loc) => {
        if (loc.city) return loc.city;
        const pin = loc.pin || '';
        if (pin.includes(',')) {
            const parts = pin.split(',');
            return parts[parts.length - 1].trim();
        }
        if (pin && pin !== 'Location') return pin.trim();
        if (loc.name && loc.name.includes(',')) {
            const parts = loc.name.split(',');
            return parts[parts.length - 1].trim();
        }
        return loc.name || 'Nashik';
    };

    const handleLoadProfile = (location) => {
        const targetRegion = extractCityFromLocation(location);
        const query = new URLSearchParams();
        if (targetRegion) query.set('region', targetRegion);
        if (location.landId) query.set('landId', location.landId);
        navigate(`/buyer/analysis?${query.toString()}`);
    };

    return (
        <Container fluid className="p-0 d-flex flex-column min-vh-100">
            <div className="flex-grow-1">
                <h2 className="text-white fw-bold mb-4">
                    <i className="bi bi-bookmarks text-primary"></i> {t('saved.title')}
                </h2>
                
                {savedLocations.length === 0 ? (
                    <div className="text-center mt-5 text-secondary">
                        <i className="bi bi-folder2-open display-1"></i>
                        <h4 className="mt-3">{t('saved.no_saved')}</h4>
                        <p>{t('saved.no_saved_desc')}</p>
                    </div>
                ) : (
                    <Row className="g-4">
                        {savedLocations.map(location => (
                            <Col md={4} key={location.id}>
                                <Card className="glass-panel border-0 text-white">
                                    <Card.Body className="p-4">
                                        <div className="d-flex justify-content-between align-items-start mb-3">
                                            <div>
                                                <h5 className="fw-bold mb-1">{location.name}</h5>
                                                <p className="text-secondary small mb-0">PIN: {location.pin}</p>
                                            </div>
                                            <i className="bi bi-geo-alt-fill text-danger fs-4"></i>
                                        </div>
                                        <div className="text-secondary small mb-3">
                                            <div><i className="bi bi-clock"></i> {t('saved.saved_on')}: {location.date}</div>
                                            <div><i className="bi bi-tags"></i> {t('saved.soil')}: {location.soil}</div>
                                        </div>
                                        <div className="d-flex gap-2">
                                            <Button 
                                                variant="primary" 
                                                size="sm" 
                                                className="flex-grow-1 fw-bold"
                                                onClick={() => handleLoadProfile(location)}
                                            >
                                                Load Profile
                                            </Button>
                                            <Button 
                                                variant="outline-danger" 
                                                size="sm" 
                                                onClick={() => handleDelete(location.id)}
                                            >
                                                <i className="bi bi-trash"></i>
                                            </Button>
                                        </div>
                                    </Card.Body>
                                </Card>
                            </Col>
                        ))}
                    </Row>
                )}
            </div>
            
            <div className="mt-5">
                <InsightsFooter />
            </div>
        </Container>
    );
}
