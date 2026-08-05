import React, { useState, useEffect } from 'react';
import { Container, Card, Badge, Form, Button, Tabs, Tab } from 'react-bootstrap';
import InsightsFooter from '../components/InsightsFooter';
import { useTranslation } from 'react-i18next';
import { forumApi } from '../api/forumApi';
import contactApi from '../api/contactApi';
import { normalizeRole } from '../utils/roleUtils';

const STORAGE_KEY = 'earthscan_expert_queries_data';
const CONTACT_STORAGE_KEY = 'earthscan_contact_queries';

const initialMockQueries = [
    { id: '1', farmer: 'Ramesh Patil', role: 'Farmer', location: 'Pune', date: '2026-06-29', title: 'Tomato leaves turning yellow', description: 'My tomato crop is 4 weeks old and the lower leaves are turning yellow with brown spots. What should I do?', status: 'Pending' },
    { id: '2', farmer: 'Suresh Kumar', role: 'Farmer', location: 'Nashik', date: '2026-06-30', title: 'Grapevine pruning timing', description: 'When is the exact best time to prune grapes this season given the delayed monsoon?', status: 'Pending' },
    { id: '3', farmer: 'Anil Desai', role: 'Farmer', location: 'Kolhapur', date: '2026-06-25', title: 'Sugarcane fertilizer ratio', description: 'What is the recommended NPK ratio for sugarcane in black cotton soil after the first harvest?', status: 'Answered', answer: 'Use a 10:26:26 NPK mix at 200kg per acre for the ratoon crop, followed by urea after 45 days.' }
];

export default function AnswerQueries() {
    const [queries, setQueries] = useState(() => {
        const saved = localStorage.getItem(STORAGE_KEY);
        if (saved) {
            try {
                const parsed = JSON.parse(saved);
                if (Array.isArray(parsed) && parsed.length > 0) return parsed;
            } catch (e) {}
        }
        return initialMockQueries;
    });

    const [replyingTo, setReplyingTo] = useState(null);
    const [replyText, setReplyText] = useState('');
    const [activeKey, setActiveKey] = useState('pending');
    const { t } = useTranslation();

    useEffect(() => {
        fetchAllQueries();
    }, []);

    const fetchAllQueries = async () => {
        let contactList = [];
        try {
            const saved = localStorage.getItem(CONTACT_STORAGE_KEY);
            if (saved) {
                const parsed = JSON.parse(saved);
                if (Array.isArray(parsed)) {
                    contactList = parsed.filter(q => q && typeof q === 'object' && !Array.isArray(q) && (q.name || q.message));
                }
            }
        } catch (e) {}

        let expertList = [];
        try {
            const saved = localStorage.getItem(STORAGE_KEY);
            if (saved) {
                const parsed = JSON.parse(saved);
                if (Array.isArray(parsed)) expertList = parsed;
            }
        } catch (e) {}

        let apiContactQueries = [];
        try {
            const res = await contactApi.getQueries();
            if (res.data && Array.isArray(res.data)) {
                apiContactQueries = res.data.filter(q => q && typeof q === 'object' && !Array.isArray(q) && (q.name || q.message));
            }
        } catch (e) {}

        let apiForumQueries = [];
        try {
            const res = await forumApi.unanswered();
            const raw = res.data;
            const unansweredList = Array.isArray(raw) ? raw : (raw?.content || []);
            if (unansweredList && unansweredList.length > 0) {
                apiForumQueries = unansweredList.map(p => ({
                    id: p.id,
                    farmer: p.authorName || 'Farmer',
                    name: p.authorName || 'Farmer',
                    role: p.authorRole || 'Farmer',
                    location: p.category || 'General',
                    email: p.category || 'General',
                    date: p.createdAt ? new Date(p.createdAt).toLocaleDateString() : 'Recent',
                    createdAt: p.createdAt || new Date().toISOString(),
                    title: p.title,
                    description: p.content,
                    message: p.content,
                    status: 'Pending'
                }));
            }
        } catch (e) {}

        const queryMap = new Map();

        // 1. Initial mock queries
        initialMockQueries.forEach(q => queryMap.set(String(q.id), q));

        // 2. Expert local storage queries
        expertList.forEach(q => {
            if (q && q.id) queryMap.set(String(q.id), q);
        });

        // 3. Contact local storage queries
        contactList.forEach(q => {
            if (q && q.id) {
                queryMap.set(String(q.id), {
                    id: q.id,
                    farmer: q.name || q.farmer || 'User',
                    name: q.name || q.farmer || 'User',
                    role: normalizeRole(q.role || q.authorRole) || 'User',
                    location: q.email || q.location || 'General',
                    email: q.email || q.location || 'General',
                    date: q.createdAt ? new Date(q.createdAt).toLocaleString() : (q.date || 'Recent'),
                    createdAt: q.createdAt,
                    title: q.subject || q.title || (q.message ? (q.message.length > 50 ? q.message.substring(0, 50) + '...' : q.message) : 'Contact Query'),
                    description: q.message || q.description || '',
                    message: q.message || q.description || '',
                    status: q.status || (q.reply ? 'Answered' : 'Pending'),
                    answer: q.reply || q.answer || '',
                    reply: q.reply || q.answer || ''
                });
            }
        });

        // 4. API Contact queries
        apiContactQueries.forEach(q => {
            if (q && q.id) {
                queryMap.set(String(q.id), {
                    id: q.id,
                    farmer: q.name || q.farmer || 'User',
                    name: q.name || q.farmer || 'User',
                    role: normalizeRole(q.role || q.authorRole) || 'User',
                    location: q.email || q.location || 'General',
                    email: q.email || q.location || 'General',
                    date: q.createdAt ? new Date(q.createdAt).toLocaleString() : (q.date || 'Recent'),
                    createdAt: q.createdAt,
                    title: q.subject || q.title || (q.message ? (q.message.length > 50 ? q.message.substring(0, 50) + '...' : q.message) : 'Contact Query'),
                    description: q.message || q.description || '',
                    message: q.message || q.description || '',
                    status: q.status || (q.reply ? 'Answered' : 'Pending'),
                    answer: q.reply || q.answer || '',
                    reply: q.reply || q.answer || ''
                });
            }
        });

        // 5. API Forum queries
        apiForumQueries.forEach(q => {
            if (q && q.id && !queryMap.has(String(q.id))) {
                queryMap.set(String(q.id), q);
            }
        });

        const combined = Array.from(queryMap.values());
        setQueries(combined);
    };

    const handleReply = (id) => {
        setReplyingTo(id);
        setReplyText('');
    };

    const submitReply = async (id) => {
        if (!replyText.trim()) return;

        const targetQuery = queries.find(q => String(q.id) === String(id));
        const rawRole = targetQuery?.role || targetQuery?.authorRole || 'User';
        const userRole = normalizeRole(rawRole) || rawRole;

        try {
            await contactApi.replyQuery(id, replyText);
        } catch (e) {}

        try {
            await forumApi.addComment(id, replyText);
        } catch (e) {}

        const updated = queries.map(q => 
            String(q.id) === String(id) ? { ...q, status: 'Answered', answer: replyText, reply: replyText } : q
        );
        setQueries(updated);
        localStorage.setItem(STORAGE_KEY, JSON.stringify(updated));

        try {
            const currentContactSaved = localStorage.getItem(CONTACT_STORAGE_KEY);
            let currentContactList = currentContactSaved ? JSON.parse(currentContactSaved) : [];
            if (!Array.isArray(currentContactList)) currentContactList = [];

            const updatedContactList = updated.map(q => ({
                id: q.id,
                name: q.name || q.farmer,
                email: q.email || q.location,
                message: q.message || q.description,
                role: q.role || 'User',
                status: q.status,
                reply: q.reply || q.answer || replyText,
                createdAt: q.createdAt || q.date
            }));

            const contactMap = new Map();
            currentContactList.forEach(c => { if (c && c.id) contactMap.set(String(c.id), c); });
            updatedContactList.forEach(c => {
                if (c && c.id) {
                    const existing = contactMap.get(String(c.id));
                    contactMap.set(String(c.id), { ...existing, ...c });
                }
            });

            localStorage.setItem(CONTACT_STORAGE_KEY, JSON.stringify(Array.from(contactMap.values())));
        } catch (e) {}

        setReplyingTo(null);
        setReplyText('');
        alert(t('queries.reply_sent', { defaultValue: 'Reply sent successfully!' }));
    };

    const pendingQueries = queries.filter(q => q.status === 'Pending');
    const answeredQueries = queries.filter(q => q.status === 'Answered');

    return (
        <Container fluid className="p-0">
            <h2 className="text-white fw-bold mb-4">
                <i className="bi bi-chat-dots text-primary"></i> {t('queries.title')}
            </h2>
            <Card className="glass-panel border-0 text-white mb-4">
                <Card.Body className="p-4">
                    <Tabs id="support-queries-tabs" activeKey={activeKey} onSelect={(k) => k && setActiveKey(k)} className="mb-4">
                        <Tab eventKey="pending" title={`${t('queries.pending_tab')} (${pendingQueries.length})`}>
                            <div className="d-flex flex-column gap-3 mt-3">
                                {pendingQueries.length === 0 ? (
                                    <div className="text-center p-5 text-secondary">
                                        <i className="bi bi-check-circle display-4 mb-3 d-block"></i>
                                        <h5>{t('queries.all_caught_up')}</h5>
                                        <p>{t('queries.no_pending')}</p>
                                    </div>
                                ) : (
                                    pendingQueries.map(q => (
                                        <Card key={q.id} className="bg-transparent border border-secondary shadow-sm">
                                            <Card.Body>
                                                <div className="d-flex justify-content-between align-items-start mb-2">
                                                    <h5 className="fw-bold mb-0 text-white">{q.title}</h5>
                                                    <Badge bg="warning" className="text-dark">{t('queries.pending_tab')}</Badge>
                                                </div>
                                                <p className="text-secondary small mb-3">
                                                    <i className="bi bi-person-circle"></i> {q.farmer} | <i className="bi bi-geo-alt"></i> {q.location} | <i className="bi bi-calendar"></i> {q.date}
                                                </p>
                                                <p className="text-light">{q.description}</p>
                                                
                                                {replyingTo === q.id ? (
                                                    <div className="mt-3 p-3 rounded" style={{ background: 'rgba(0,0,0,0.3)' }}>
                                                        <Form.Group className="mb-3">
                                                            <Form.Label className="small text-info"><i className="bi bi-pen"></i> {t('queries.expert_advice')}</Form.Label>
                                                            <Form.Control as="textarea" rows={3} className="bg-dark text-white border-secondary" value={replyText} onChange={e => setReplyText(e.target.value)} placeholder={t('queries.placeholder_reply')} />
                                                        </Form.Group>
                                                        <div className="d-flex gap-2 justify-content-end">
                                                            <Button variant="outline-light" size="sm" onClick={() => setReplyingTo(null)}>{t('common.cancel')}</Button>
                                                            <Button variant="primary" size="sm" onClick={() => submitReply(q.id)} disabled={!replyText.trim()}>{t('queries.send_reply')}</Button>
                                                        </div>
                                                    </div>
                                                ) : (
                                                    <Button variant="outline-primary" size="sm" onClick={() => handleReply(q.id)}>
                                                        <i className="bi bi-reply-fill"></i> {t('queries.write_reply')}
                                                    </Button>
                                                )}
                                            </Card.Body>
                                        </Card>
                                    ))
                                )}
                            </div>
                        </Tab>
                        <Tab eventKey="answered" title={`${t('queries.answered_tab')} (${answeredQueries.length})`}>
                            <div className="d-flex flex-column gap-3 mt-3">
                                {answeredQueries.map(q => (
                                    <Card key={q.id} className="bg-transparent border border-secondary shadow-sm">
                                        <Card.Body>
                                            <div className="d-flex justify-content-between align-items-start mb-2">
                                                <h5 className="fw-bold mb-0 text-white">{q.title}</h5>
                                                <Badge bg="success">{t('queries.answered_tab')}</Badge>
                                            </div>
                                            <p className="text-secondary small mb-3">
                                                <i className="bi bi-person-circle"></i> {q.farmer} | <i className="bi bi-geo-alt"></i> {q.location}
                                            </p>
                                            <p className="text-light mb-3">{q.description}</p>
                                            <div className="p-3 rounded border border-success" style={{ background: 'rgba(0, 230, 118, 0.05)' }}>
                                                <div className="text-success small fw-bold mb-1"><i className="bi bi-shield-check"></i> {t('queries.expert_reply')}</div>
                                                <p className="mb-0 text-light">{q.answer}</p>
                                            </div>
                                        </Card.Body>
                                    </Card>
                                ))}
                            </div>
                        </Tab>
                    </Tabs>
                </Card.Body>
            </Card>

            <InsightsFooter />
        </Container>
    );
}
