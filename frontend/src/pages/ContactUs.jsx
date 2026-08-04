import React, { useState, useEffect, useContext } from 'react';
import { Container, Row, Col, Card, Form, Button, Alert } from 'react-bootstrap';
import { useNavigate } from 'react-router-dom';
import { AuthContext } from '../context/AuthContext';

export default function ContactUs() {
    const { user } = useContext(AuthContext) || {};

    const activeUser = React.useMemo(() => {
        if (user) return user;
        try {
            const stored = localStorage.getItem('earthscan_user');
            return stored ? JSON.parse(stored) : null;
        } catch {
            return null;
        }
    }, [user]);

    const [name, setName] = useState('');
    const [email, setEmail] = useState('');
    const [message, setMessage] = useState('');
    const [submitted, setSubmitted] = useState(false);
    const [loading, setLoading] = useState(false);

    useEffect(() => {
        if (activeUser) {
            const userName = activeUser.name || activeUser.fullName || activeUser.username || '';
            const userEmail = activeUser.email || '';
            if (userName) setName(userName);
            if (userEmail) setEmail(userEmail);
        }
    }, [activeUser]);

    const navigate = useNavigate();

    const handleSubmit = (e) => {
        e.preventDefault();
        setLoading(true);
        setTimeout(() => {
            setLoading(false);
            setSubmitted(true);
            setMessage('');
            setTimeout(() => setSubmitted(false), 5000);
        }, 1000);
    };

    return (
        <div style={{
            background: 'url("https://images.unsplash.com/photo-1592982537447-6f2a6a0a38cc?q=80&w=2070&auto=format&fit=crop") no-repeat center center fixed',
            backgroundSize: 'cover',
            minHeight: '100vh',
            display: 'flex',
            alignItems: 'center'
        }}>
            <Container>
                <Row className="justify-content-center">
                    <Col md={10} lg={8}>
                        <Card className="glass-panel text-white border-0 shadow-lg p-5" style={{ background: 'rgba(10, 15, 24, 0.85)', backdropFilter: 'blur(10px)' }}>
                            <Card.Body>
                                <div className="text-center mb-5">
                                    <h1 className="fw-bold mb-2">Contact Us</h1>
                                    <p className="text-secondary">We'd love to hear from you. Drop us a message!</p>
                                </div>
                                <Row className="g-5">
                                    <Col md={6}>
                                        <Form onSubmit={handleSubmit}>
                                            {submitted && (
                                                <Alert variant="success" className="py-2 small">
                                                    Your message has been sent successfully. We will get back to you soon!
                                                </Alert>
                                            )}
                                            <Form.Group className="mb-3">
                                                <Form.Label className="small text-secondary">Your Name</Form.Label>
                                                <Form.Control type="text" placeholder="Enter your name" className="bg-transparent text-white border-secondary shadow-none" value={name} onChange={e => setName(e.target.value)} required />
                                            </Form.Group>
                                            <Form.Group className="mb-3">
                                                <Form.Label className="small text-secondary">Email Address</Form.Label>
                                                <Form.Control type="email" placeholder="Email" className="bg-transparent text-white border-secondary shadow-none" value={email} onChange={e => setEmail(e.target.value)} required />
                                            </Form.Group>
                                            <Form.Group className="mb-4">
                                                <Form.Label className="small text-secondary">Message</Form.Label>
                                                <Form.Control as="textarea" rows={4} placeholder="How can we help?" className="bg-transparent text-white border-secondary shadow-none" value={message} onChange={e => setMessage(e.target.value)} required />
                                            </Form.Group>
                                            <Button type="submit" variant="primary" className="w-100 py-2 fw-bold border-0" style={{ background: 'linear-gradient(90deg, #2979ff, #1c54b2)' }} disabled={loading}>
                                                {loading ? 'Sending...' : 'Send Message'}
                                            </Button>
                                        </Form>
                                    </Col>
                                    <Col md={6}>
                                        <div className="d-flex flex-column gap-4 h-100 justify-content-center">
                                            <div className="d-flex align-items-start gap-3">
                                                <div className="bg-primary rounded-circle d-flex align-items-center justify-content-center flex-shrink-0" style={{ width: '50px', height: '50px', flexShrink: 0, background: 'rgba(41, 121, 255, 0.2)' }}>
                                                    <i className="bi bi-geo-alt-fill text-primary fs-4"></i>
                                                </div>
                                                <div className="pt-1">
                                                    <h6 className="fw-bold mb-1">Our Office</h6>
                                                    <p className="text-secondary small mb-0">East Court, Phoenix Marketcity, Clover Park, Viman Nagar, Pune - 411014</p>
                                                </div>
                                            </div>
                                            <div className="d-flex align-items-start gap-3">
                                                <div className="bg-success rounded-circle d-flex align-items-center justify-content-center flex-shrink-0" style={{ width: '50px', height: '50px', flexShrink: 0, background: 'rgba(0, 230, 118, 0.2)' }}>
                                                    <i className="bi bi-envelope-fill text-success fs-4"></i>
                                                </div>
                                                <div className="pt-1">
                                                    <h6 className="fw-bold mb-1">Email Us</h6>
                                                    <p className="text-secondary small mb-0">support@earthscanbharat.in</p>
                                                </div>
                                            </div>
                                            <div className="d-flex align-items-start gap-3">
                                                <div className="bg-warning rounded-circle d-flex align-items-center justify-content-center flex-shrink-0" style={{ width: '50px', height: '50px', flexShrink: 0, background: 'rgba(255, 193, 7, 0.2)' }}>
                                                    <i className="bi bi-telephone-fill text-warning fs-4"></i>
                                                </div>
                                                <div className="pt-1">
                                                    <h6 className="fw-bold mb-1">Call Us</h6>
                                                    <p className="text-secondary small mb-0">+91-8446342686</p>
                                                </div>
                                            </div>
                                        </div>
                                    </Col>
                                </Row>
                                <div className="text-center mt-5">
                                    <Button onClick={() => navigate(-1)} variant="link" className="text-secondary text-decoration-none small shadow-none">Go Back</Button>
                                </div>
                            </Card.Body>
                        </Card>
                    </Col>
                </Row>
            </Container>
        </div>
    );
}
