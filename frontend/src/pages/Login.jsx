import React, { useState, useContext } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { AuthContext } from '../context/AuthContext';
import { useTranslation } from 'react-i18next';
import { Container, Row, Col, Form, Button, Alert, Card, Modal } from 'react-bootstrap';
import LanguageSelector from '../components/LanguageSelector';
import { authApi } from '../api/authApi';

import { normalizeRole } from '../utils/roleUtils';
import { validateEmail, validatePassword } from '../utils/validation';

export default function Login() {
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [error, setError] = useState('');
    const { login } = useContext(AuthContext);
    const navigate = useNavigate();
    const { t } = useTranslation();

    // Forgot Password State
    const [showForgot, setShowForgot] = useState(false);
    const [forgotEmail, setForgotEmail] = useState('');
    const [newPassword, setNewPassword] = useState('');
    const [forgotLoading, setForgotLoading] = useState(false);
    const [forgotMessage, setForgotMessage] = useState('');
    const [forgotError, setForgotError] = useState('');

    const handleResetPassword = async (e) => {
        e.preventDefault();
        setForgotMessage('');
        setForgotError('');

        const emailErr = validateEmail(forgotEmail);
        if (emailErr) {
            setForgotError(emailErr);
            return;
        }

        const passErr = validatePassword(newPassword);
        if (passErr) {
            setForgotError(passErr);
            return;
        }

        setForgotLoading(true);
        
        try {
            const response = await authApi.resetPassword(forgotEmail, newPassword);
            setForgotMessage(response.data?.message || 'Password reset successfully');
            setTimeout(() => {
                setShowForgot(false);
                setForgotMessage('');
                setForgotEmail('');
                setNewPassword('');
            }, 3000);
        } catch (error) {
            console.error('Error resetting password:', error);
            setForgotError(error.response?.data?.message || error.message || 'Failed to reset password');
        } finally {
            setForgotLoading(false);
        }
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        setError('');
        const result = await login(email, password);
        if (result.success) {
            const userRole = normalizeRole(result.user?.role || result.user?.Role);
            if (userRole === 'Admin') navigate('/admin');
            else if (userRole === 'Land Buyer') navigate('/search');
            else if (userRole === 'Agriculture Expert') navigate('/expert/manage-crop');
            else navigate('/');
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
                                <h2 className="fw-bold mb-0">{t('login.welcome')}</h2>
                                <p className="text-secondary">{t('login.sign_in')}</p>
                            </div>

                            {error && <Alert variant="danger" className="border-0 bg-danger text-white bg-opacity-75">{error}</Alert>}

                            <Form onSubmit={handleSubmit}>
                                <Form.Group className="mb-3">
                                    <Form.Label className="text-secondary small">{t('login.email')}</Form.Label>
                                    <Form.Control
                                        type="email"
                                        value={email}
                                        onChange={(e) => setEmail(e.target.value)}
                                        required
                                        className="bg-transparent text-white border-secondary shadow-none"
                                        placeholder="Email"
                                        style={{ borderColor: 'rgba(255,255,255,0.2)' }}
                                    />
                                </Form.Group>

                                <Form.Group className="mb-4">
                                    <div className="d-flex justify-content-between align-items-center mb-1">
                                        <Form.Label className="text-secondary small mb-0">{t('login.password')}</Form.Label>
                                        <Button variant="link" className="text-primary text-decoration-none small p-0 m-0 shadow-none" onClick={() => setShowForgot(true)}>
                                            {t('login.forgot_pass')}
                                        </Button>
                                    </div>
                                    <Form.Control
                                        type="password"
                                        value={password}
                                        onChange={(e) => setPassword(e.target.value)}
                                        required
                                        className="bg-transparent text-white border-secondary shadow-none"
                                        placeholder="Password"
                                        style={{ borderColor: 'rgba(255,255,255,0.2)' }}
                                    />
                                </Form.Group>

                                <Button 
                                    variant="success" 
                                    type="submit" 
                                    className="w-100 py-2 fw-bold mb-3 border-0"
                                    style={{ background: 'linear-gradient(90deg, #00e676, #00b259)' }}
                                >
                                    {t('login.login_btn')}
                                </Button>
                                
                                <div className="text-center mt-3">
                                    <span className="text-secondary small">{t('login.no_account')} </span>
                                    <Link to="/register" className="text-primary text-decoration-none small fw-bold">{t('login.sign_up')}</Link>
                                </div>
                                <div className="text-center mt-3 d-flex justify-content-center gap-3">
                                    <Link to="/about" className="text-secondary text-decoration-none small hover-white">{t('login.about')}</Link>
                                    <span className="text-secondary small">|</span>
                                    <Link to="/contact" className="text-secondary text-decoration-none small hover-white">{t('login.contact')}</Link>
                                </div>
                            </Form>
                        </Card.Body>
                    </Card>
                </Col>
            </Row>
        </Container>

        {/* Forgot Password Modal */}
        <Modal show={showForgot} onHide={() => setShowForgot(false)} centered contentClassName="bg-dark text-white border-secondary">
            <Modal.Header closeButton closeVariant="white" className="border-secondary">
                <Modal.Title>{t('login.reset_pass')}</Modal.Title>
            </Modal.Header>
            <Form onSubmit={handleResetPassword}>
                <Modal.Body>
                    {forgotMessage && <Alert variant="success" className="py-2 small">{forgotMessage}</Alert>}
                    {forgotError && <Alert variant="danger" className="py-2 small border-0 bg-danger text-white bg-opacity-75">{forgotError}</Alert>}
                    <Form.Group className="mb-3">
                        <Form.Label className="small text-secondary">Email Address</Form.Label>
                        <Form.Control 
                            type="email" 
                            required 
                            className="bg-transparent text-white border-secondary shadow-none" 
                            value={forgotEmail} 
                            onChange={e => setForgotEmail(e.target.value)} 
                            placeholder="Enter your registered email"
                        />
                    </Form.Group>
                    <Form.Group className="mb-3">
                        <Form.Label className="small text-secondary">New Password</Form.Label>
                        <Form.Control 
                            type="password" 
                            required 
                            className="bg-transparent text-white border-secondary shadow-none" 
                            value={newPassword} 
                            onChange={e => setNewPassword(e.target.value)} 
                            placeholder="Enter new password"
                        />
                        <Form.Text className="text-secondary small d-block mt-1">
                            Must be 8–72 characters long and contain at least 1 letter and 1 digit.
                        </Form.Text>
                    </Form.Group>
                </Modal.Body>
                <Modal.Footer className="border-secondary">
                    <Button variant="outline-light" onClick={() => setShowForgot(false)}>{t('login.cancel')}</Button>
                    <Button variant="success" type="submit" disabled={forgotLoading}>
                        {forgotLoading ? t('login.resetting') : t('login.reset_btn')}
                    </Button>
                </Modal.Footer>
            </Form>
        </Modal>

        </div>
    );
}
