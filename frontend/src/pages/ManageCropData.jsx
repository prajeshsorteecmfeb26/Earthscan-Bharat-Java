import React, { useState } from 'react';
import { Container, Card, Table, Button, Badge, Form, Modal } from 'react-bootstrap';
import InsightsFooter from '../components/InsightsFooter';
import { useTranslation } from 'react-i18next';

const CROP_STORAGE_KEY = 'earthscan_crop_data';

const initialCrops = [
    { id: 1, name: 'Sugarcane', season: 'Kharif', soil: 'Black Cotton', duration: '12-18 Months', status: 'Active' },
    { id: 2, name: 'Wheat', season: 'Rabi', soil: 'Alluvial', duration: '4-5 Months', status: 'Active' },
    { id: 3, name: 'Cotton', season: 'Kharif', soil: 'Black Soil', duration: '5-6 Months', status: 'Review Needed' },
    { id: 4, name: 'Alphonso Mango', season: 'Perennial', soil: 'Laterite', duration: 'Tree', status: 'Active' }
];

export default function ManageCropData() {
    const [crops, setCrops] = useState(() => {
        const saved = localStorage.getItem(CROP_STORAGE_KEY);
        if (saved) {
            try {
                const parsed = JSON.parse(saved);
                if (Array.isArray(parsed) && parsed.length > 0) return parsed;
            } catch (e) {}
        }
        return initialCrops;
    });

    const [showModal, setShowModal] = useState(false);
    const [currentCrop, setCurrentCrop] = useState({ name: '', season: 'Kharif', soil: '', duration: '' });
    const { t } = useTranslation();

    const handleSave = () => {
        if (!currentCrop.name.trim()) return;
        let updated;
        if (currentCrop.id) {
            updated = crops.map(c => c.id === currentCrop.id ? currentCrop : c);
        } else {
            updated = [...crops, { ...currentCrop, id: Date.now(), status: 'Active' }];
        }
        setCrops(updated);
        localStorage.setItem(CROP_STORAGE_KEY, JSON.stringify(updated));
        setShowModal(false);
    };

    const handleDelete = (id) => {
        if (window.confirm('Are you sure you want to delete this crop protocol?')) {
            const updated = crops.filter(c => c.id !== id);
            setCrops(updated);
            localStorage.setItem(CROP_STORAGE_KEY, JSON.stringify(updated));
        }
    };

    const handleEdit = (crop) => {
        setCurrentCrop(crop);
        setShowModal(true);
    };

    const handleAddNew = () => {
        setCurrentCrop({ name: '', season: 'Kharif', soil: '', duration: '' });
        setShowModal(true);
    };

    return (
        <Container fluid className="p-0">
            <div className="d-flex justify-content-between align-items-center mb-4">
                <h2 className="text-white fw-bold mb-0">
                    <i className="bi bi-journal-check text-success"></i> {t('crop_mgmt.title')}
                </h2>
                <Button variant="success" className="rounded-pill fw-bold" onClick={handleAddNew}>
                    <i className="bi bi-plus-lg"></i> {t('crop_mgmt.add_btn')}
                </Button>
            </div>

            <Card className="glass-panel border-0 text-white mb-4">
                <Card.Body className="p-4">
                    <div className="table-responsive">
                        <Table variant="dark" hover className="align-middle border-secondary mb-0" style={{ backgroundColor: 'transparent' }}>
                            <thead>
                                <tr>
                                    <th className="bg-transparent text-secondary border-secondary">{t('crop_mgmt.crop_name')}</th>
                                    <th className="bg-transparent text-secondary border-secondary">{t('crop_mgmt.season')}</th>
                                    <th className="bg-transparent text-secondary border-secondary">{t('crop_mgmt.soil')}</th>
                                    <th className="bg-transparent text-secondary border-secondary">{t('crop_mgmt.duration')}</th>
                                    <th className="bg-transparent text-secondary border-secondary">{t('crop_mgmt.status')}</th>
                                    <th className="bg-transparent text-secondary border-secondary text-end">{t('crop_mgmt.actions')}</th>
                                </tr>
                            </thead>
                            <tbody>
                                {crops.map(crop => (
                                    <tr key={crop.id}>
                                        <td className="bg-transparent border-secondary fw-bold">{crop.name}</td>
                                        <td className="bg-transparent border-secondary">{crop.season}</td>
                                        <td className="bg-transparent border-secondary">{crop.soil}</td>
                                        <td className="bg-transparent border-secondary">{crop.duration}</td>
                                        <td className="bg-transparent border-secondary">
                                            <Badge bg={crop.status === 'Active' ? 'success' : 'warning'} className="text-dark">
                                                {crop.status === 'Active' ? t('crop_mgmt.active') : t('crop_mgmt.review')}
                                            </Badge>
                                        </td>
                                        <td className="bg-transparent border-secondary text-end">
                                            <div className="d-flex justify-content-end gap-2">
                                                <Button variant="outline-primary" size="sm" className="rounded-pill px-3" onClick={() => handleEdit(crop)}>
                                                    <i className="bi bi-pencil-square me-1"></i> Edit
                                                </Button>
                                                <Button variant="outline-danger" size="sm" className="rounded-pill px-3" onClick={() => handleDelete(crop.id)}>
                                                    <i className="bi bi-trash-fill me-1"></i> Delete
                                                </Button>
                                            </div>
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </Table>
                    </div>
                </Card.Body>
            </Card>

            <Modal show={showModal} onHide={() => setShowModal(false)} centered contentClassName="bg-dark text-white border-secondary">
                <Modal.Header closeButton closeVariant="white" className="border-secondary">
                    <Modal.Title>{currentCrop.id ? t('crop_mgmt.edit_title') : t('crop_mgmt.add_title')}</Modal.Title>
                </Modal.Header>
                <Modal.Body>
                    <Form>
                        <Form.Group className="mb-3">
                            <Form.Label>{t('crop_mgmt.crop_name')}</Form.Label>
                            <Form.Control type="text" className="bg-transparent text-white border-secondary" value={currentCrop.name} onChange={e => setCurrentCrop({...currentCrop, name: e.target.value})} />
                        </Form.Group>
                        <Form.Group className="mb-3">
                            <Form.Label>{t('crop_mgmt.season')}</Form.Label>
                            <Form.Select className="bg-transparent text-white border-secondary" value={currentCrop.season} onChange={e => setCurrentCrop({...currentCrop, season: e.target.value})}>
                                <option value="Kharif" className="text-dark">Kharif</option>
                                <option value="Rabi" className="text-dark">Rabi</option>
                                <option value="Zaid" className="text-dark">Zaid</option>
                                <option value="Perennial" className="text-dark">Perennial</option>
                            </Form.Select>
                        </Form.Group>
                        <Form.Group className="mb-3">
                            <Form.Label>{t('crop_mgmt.soil')}</Form.Label>
                            <Form.Control type="text" className="bg-transparent text-white border-secondary" value={currentCrop.soil} onChange={e => setCurrentCrop({...currentCrop, soil: e.target.value})} />
                        </Form.Group>
                        <Form.Group className="mb-3">
                            <Form.Label>{t('crop_mgmt.duration')}</Form.Label>
                            <Form.Control type="text" className="bg-transparent text-white border-secondary" placeholder="e.g. 4-5 Months" value={currentCrop.duration} onChange={e => setCurrentCrop({...currentCrop, duration: e.target.value})} />
                        </Form.Group>
                    </Form>
                </Modal.Body>
                <Modal.Footer className="border-secondary">
                    <Button variant="outline-light" onClick={() => setShowModal(false)}>{t('crop_mgmt.cancel')}</Button>
                    <Button variant="success" onClick={handleSave}>{t('crop_mgmt.save_btn')}</Button>
                </Modal.Footer>
            </Modal>

            <InsightsFooter />
        </Container>
    );
}
