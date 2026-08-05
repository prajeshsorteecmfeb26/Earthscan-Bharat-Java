import React, { useState, useEffect, useContext } from 'react';
import { Container, Row, Col, Card, Form, Button, Alert } from 'react-bootstrap';
import { useNavigate } from 'react-router-dom';
import { AuthContext } from '../context/AuthContext';

import contactApi from '../api/contactApi';

const STORAGE_KEY = 'earthscan_contact_queries';

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
    const [userQueries, setUserQueries] = useState([]);

    useEffect(() => {
        if (activeUser) {
            const userName = activeUser.name || activeUser.fullName || activeUser.username || '';
            const userEmail = activeUser.email || '';
            if (userName) setName(userName);
            if (userEmail) setEmail(userEmail);
        }
        fetchUserQueries();
    }, [activeUser]);

    const fetchUserQueries = async () => {
        let localList = [];
        try {
            const saved = localStorage.getItem(STORAGE_KEY);
            if (saved) {
                const parsed = JSON.parse(saved);
                if (Array.isArray(parsed)) {
                    localList = parsed.filter(q => q && typeof q === 'object' && !Array.isArray(q) && (q.name || q.message));
                }
            }
        } catch (e) {}

        try {
            const res = await contactApi.getQueries();
            if (res.data && Array.isArray(res.data)) {
                const apiList = res.data.filter(q => q && typeof q === 'object' && !Array.isArray(q) && (q.name || q.message));
                const mergedMap = new Map();
                [...localList, ...apiList].forEach(q => {
                    if (q.id) mergedMap.set(String(q.id), q);
                });
                const merged = Array.from(mergedMap.values());
                setUserQueries(merged);
                localStorage.setItem(STORAGE_KEY, JSON.stringify(merged));
                return;
            }
        } catch (e) {}

        setUserQueries(localList);
    };

    const navigate = useNavigate();

    const handleSubmit = async (e) => {
        e.preventDefault();
        if (!name.trim() || !email.trim() || !message.trim()) return;

        setLoading(true);
        try {
            const userRole = activeUser?.role || activeUser?.Role || 'Farmer';
            const payload = { name: name.trim(), email: email.trim(), message: message.trim(), role: userRole };
            let created = null;

            try {
                const res = await contactApi.submitQuery(payload);
                if (res.data && typeof res.data === 'object' && !Array.isArray(res.data) && res.data.id) {
                    created = { ...res.data, role: res.data.role || userRole };
                }
            } catch (err) {}

            if (!created) {
                created = {
                    id: 'cq-' + Date.now() + '-' + Math.random().toString(36).substring(2, 7),
                    name: name.trim(),
                    email: email.trim(),
                    message: message.trim(),
                    role: userRole,
                    status: 'Pending',
                    reply: '',
                    createdAt: new Date().toISOString()
                };
            }

            const validCurrent = userQueries.filter(q => q && typeof q === 'object' && !Array.isArray(q) && (q.name || q.message));
            const updated = [created, ...validCurrent.filter(q => String(q.id) !== String(created.id))];
            setUserQueries(updated);
            localStorage.setItem(STORAGE_KEY, JSON.stringify(updated));

            setSubmitted(true);
            setMessage('');
            setTimeout(() => setSubmitted(false), 5000);
        } catch (error) {
            console.error('Error submitting contact query:', error);
        } finally {
            setLoading(false);
        }
    };

    const handleDeleteQuery = async (id) => {
        if (!window.confirm('Are you sure you want to delete this message?')) return;

        try {
            await contactApi.deleteQuery(id);
        } catch (e) {}

        const updated = userQueries.filter(q => String(q.id) !== String(id));
        setUserQueries(updated);
        localStorage.setItem(STORAGE_KEY, JSON.stringify(updated));

        try {
            const expertSaved = localStorage.getItem('earthscan_expert_queries_data');
            if (expertSaved) {
                const parsed = JSON.parse(expertSaved);
                if (Array.isArray(parsed)) {
                    const filteredExpert = parsed.filter(q => String(q.id) !== String(id));
                    localStorage.setItem('earthscan_expert_queries_data', JSON.stringify(filteredExpert));
                }
            }
        } catch (e) {}
    };

    const currentUserEmail = (activeUser?.email || activeUser?.Email || email || '').toLowerCase().trim();

    const validQueries = userQueries.filter(q => {
        if (!q || typeof q !== 'object' || Array.isArray(q) || (!q.name && !q.message)) return false;
        if (!currentUserEmail) return false;
        const queryEmail = (q.email || '').toLowerCase().trim();
        return queryEmail === currentUserEmail;
    });

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

                                {validQueries && validQueries.length > 0 && (
                                    <div className="mt-5 pt-4 border-top border-secondary">
                                        <h5 className="fw-bold mb-3 d-flex align-items-center gap-2">
                                            <i className="bi bi-chat-left-text-fill text-info"></i>
                                            My Messages & Admin Responses
                                        </h5>
                                        <div className="d-flex flex-column gap-3" style={{ maxHeight: '350px', overflowY: 'auto' }}>
                                            {validQueries.map((q, idx) => (
                                                <div key={q.id || idx} className="p-3 rounded border border-secondary" style={{ background: 'rgba(255,255,255,0.04)' }}>
                                                    <div className="d-flex justify-content-between align-items-center mb-2">
                                                        <span className="fw-bold text-white small">
                                                            <i className="bi bi-person-fill text-info me-1"></i>
                                                            {q.name} <span className="text-secondary font-monospace">({q.email})</span>
                                                        </span>
                                                        <div className="d-flex align-items-center gap-2">
                                                            <span className={`badge bg-${q.status === 'Answered' ? 'success' : 'warning'} px-2 py-1`}>
                                                                {q.status === 'Answered' ? 'Resolved / Answered' : 'Pending Admin Response'}
                                                            </span>
                                                            <Button 
                                                                variant="outline-danger" 
                                                                size="sm" 
                                                                className="py-0 px-2 rounded-pill shadow-none border-0" 
                                                                title="Delete Query"
                                                                onClick={() => handleDeleteQuery(q.id)}
                                                            >
                                                                <i className="bi bi-trash3-fill"></i>
                                                            </Button>
                                                        </div>
                                                    </div>
                                                    <p className="text-light small mb-1" style={{ whiteSpace: 'pre-wrap' }}>{q.message}</p>
                                                    {q.createdAt && (
                                                        <div className="text-secondary extra-small mb-2" style={{ fontSize: '0.75rem' }}>
                                                            Submitted: {new Date(q.createdAt).toLocaleString()}
                                                        </div>
                                                    )}
                                                    {q.reply && (
                                                        <div className="p-3 mt-2 rounded border border-success" style={{ background: 'rgba(25, 135, 84, 0.2)' }}>
                                                            <div className="fw-bold text-success small mb-1 d-flex align-items-center gap-1">
                                                                <i className="bi bi-shield-check text-success fs-6"></i> Admin Response & Feedback:
                                                            </div>
                                                            <div className="text-white small" style={{ whiteSpace: 'pre-wrap' }}>{q.reply}</div>
                                                        </div>
                                                    )}
                                                </div>
                                            ))}
                                        </div>
                                    </div>
                                )}

                                <div className="text-center mt-4">
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
