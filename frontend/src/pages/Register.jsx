import React, { useState, useContext } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { AuthContext } from '../context/AuthContext';
import { Container, Row, Col, Form, Button, Alert, Card, Modal } from 'react-bootstrap';
import LanguageSelector from '../components/LanguageSelector';
import { useTranslation } from 'react-i18next';

export default function Register() {
    const [name, setName] = useState('');
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [role, setRole] = useState('Farmer');
    const [error, setError] = useState('');
    const [showSuccess, setShowSuccess] = useState(false);
    const { register } = useContext(AuthContext);
    const navigate = useNavigate();
    const { t } = useTranslation();

    const handleSubmit = async (e) => {
        e.preventDefault();
        setError('');
        const result = await register(name, email, password, role);
        if (result.success) {
            setShowSuccess(true);
            setTimeout(() => {
                setShowSuccess(false);
                navigate('/login');
            }, 2000);
        } else {
            setError(result.message);
        }
    };

    return (
        <div style={{
            background: 'url("https://images.unsplash.com/photo-1592982537447-6f2a6a0a38cc?q=80&w=2070&auto=format&fit=crop") no-repeat center center fixed',
            backgroundSize: 'cover',
            minHeight: '100vh',
            display: 'flex',
            alignItems: 'center'
        }}>
        <LanguageSelector floating />
        <Container fluid className="d-flex align-items-center justify-content-center">
            <Row className="w-100 justify-content-center">
                <Col md={6} lg={4}>
                    <Card className="glass-panel text-white border-0 shadow-lg p-4">
                        <Card.Body>
                            <div className="text-center mb-4">
                                <h2 className="fw-bold mb-0">{t('register.title')}</h2>
                                <p className="text-secondary">{t('register.subtitle')}</p>
                            </div>

                            {error && <Alert variant="danger" className="border-0 bg-danger text-white bg-opacity-75">{error}</Alert>}

                            <Form onSubmit={handleSubmit}>
                                <Form.Group className="mb-3">
                                    <Form.Label className="text-secondary small">{t('register.full_name')}</Form.Label>
                                    <Form.Control
                                        type="text"
                                        value={name}
                                        onChange={(e) => setName(e.target.value)}
                                        required
                                        className="bg-transparent text-white border-secondary shadow-none"
                                        placeholder={t('register.placeholder_name')}
                                    />
                                </Form.Group>

                                <Form.Group className="mb-3">
                                    <Form.Label className="text-secondary small">{t('register.email')}</Form.Label>
                                    <Form.Control
                                        type="email"
                                        value={email}
                                        onChange={(e) => setEmail(e.target.value)}
                                        required
                                        className="bg-transparent text-white border-secondary shadow-none"
                                        placeholder={t('register.placeholder_email')}
                                    />
                                </Form.Group>

                                <Form.Group className="mb-3">
                                    <Form.Label className="text-secondary small">{t('register.password')}</Form.Label>
                                    <Form.Control
                                        type="password"
                                        value={password}
                                        onChange={(e) => setPassword(e.target.value)}
                                        required
                                        className="bg-transparent text-white border-secondary shadow-none"
                                        placeholder={t('register.placeholder_pass')}
                                    />
                                </Form.Group>

                                <Form.Group className="mb-4">
                                    <Form.Label className="text-secondary small">{t('register.role')}</Form.Label>
                                    <Form.Select 
                                        value={role} 
                                        onChange={(e) => setRole(e.target.value)}
                                        className="bg-transparent text-white border-secondary shadow-none"
                                        style={{ backgroundColor: '#141d2b' }}
                                    >
                                        <option value="Farmer" className="bg-dark">{t('register.farmer')}</option>
                                        <option value="Land Buyer" className="bg-dark">{t('register.buyer')}</option>
                                        <option value="Agriculture Expert" className="bg-dark">{t('register.expert')}</option>
                                        <option value="Admin" className="bg-dark text-warning">{t('register.admin')}</option>
                                    </Form.Select>
                                </Form.Group>

                                <Button 
                                    variant="primary" 
                                    type="submit" 
                                    className="w-100 py-2 fw-bold mb-3 border-0"
                                    style={{ background: 'linear-gradient(90deg, #2979ff, #1c54b2)' }}
                                >
                                    {t('register.sign_up')}
                                </Button>
                                
                                <div className="text-center mt-3">
                                    <span className="text-secondary small">{t('register.already_account')} </span>
                                    <Link to="/login" className="text-primary text-decoration-none small fw-bold">{t('register.login_here')}</Link>
                                </div>
                                <div className="text-center mt-3 d-flex justify-content-center gap-3">
                                    <Link to="/about" className="text-secondary text-decoration-none small hover-white">{t('register.about')}</Link>
                                    <span className="text-secondary small">|</span>
                                    <Link to="/contact" className="text-secondary text-decoration-none small hover-white">{t('register.contact')}</Link>
                                </div>
                            </Form>
                        </Card.Body>
                    </Card>
                </Col>
            </Row>

            <Modal show={showSuccess} centered contentClassName="bg-dark text-white border-success" backdrop="static">
                <Modal.Body className="text-center p-5">
                    <i className="bi bi-check-circle-fill text-success" style={{ fontSize: '4rem' }}></i>
                    <h3 className="mt-3 fw-bold">{t('register.success_title')}</h3>
                    <p className="text-secondary mb-0">{t('register.redirecting')}</p>
                </Modal.Body>
            </Modal>
        </Container>
        </div>
    );
}
