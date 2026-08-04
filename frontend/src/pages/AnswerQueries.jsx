import React, { useState, useEffect } from 'react';
import { Container, Card, Badge, Form, Button, Tabs, Tab } from 'react-bootstrap';
import InsightsFooter from '../components/InsightsFooter';
import { useTranslation } from 'react-i18next';
import { forumApi } from '../api/forumApi';

const STORAGE_KEY = 'earthscan_expert_queries_data';

const initialMockQueries = [
    { id: '1', farmer: 'Ramesh Patil', location: 'Pune', date: '2026-06-29', title: 'Tomato leaves turning yellow', description: 'My tomato crop is 4 weeks old and the lower leaves are turning yellow with brown spots. What should I do?', status: 'Pending' },
    { id: '2', farmer: 'Suresh Kumar', location: 'Nashik', date: '2026-06-30', title: 'Grapevine pruning timing', description: 'When is the exact best time to prune grapes this season given the delayed monsoon?', status: 'Pending' },
    { id: '3', farmer: 'Anil Desai', location: 'Kolhapur', date: '2026-06-25', title: 'Sugarcane fertilizer ratio', description: 'What is the recommended NPK ratio for sugarcane in black cotton soil after the first harvest?', status: 'Answered', answer: 'Use a 10:26:26 NPK mix at 200kg per acre for the ratoon crop, followed by urea after 45 days.' }
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
    const { t } = useTranslation();

    useEffect(() => {
        fetchUnansweredForumPosts();
    }, []);

    const fetchUnansweredForumPosts = async () => {
        try {
            const response = await forumApi.unanswered();
            const raw = response.data;
            const unansweredList = Array.isArray(raw) ? raw : (raw?.content || []);
            if (unansweredList && unansweredList.length > 0) {
                setQueries(prev => {
                    const existingIds = new Set(prev.map(q => String(q.id)));
                    const newItems = unansweredList
                        .filter(p => !existingIds.has(String(p.id)))
                        .map(p => ({
                            id: p.id,
                            farmer: p.authorName || 'Farmer',
                            location: p.category || 'General',
                            date: p.createdAt ? new Date(p.createdAt).toLocaleDateString() : 'Recent',
                            title: p.title,
                            description: p.content,
                            status: 'Pending'
                        }));
                    if (newItems.length === 0) return prev;
                    const updated = [...newItems, ...prev];
                    localStorage.setItem(STORAGE_KEY, JSON.stringify(updated));
                    return updated;
                });
            }
        } catch (err) {
            console.log('Using local expert queries');
        }
    };

    const handleReply = (id) => {
        setReplyingTo(id);
        setReplyText('');
    };

    const submitReply = async (id) => {
        if (!replyText.trim()) return;

        try {
            await forumApi.addComment(id, replyText);
        } catch (e) {
            // Ignore API error for mock static queries
        }

        const updated = queries.map(q => 
            String(q.id) === String(id) ? { ...q, status: 'Answered', answer: replyText } : q
        );
        setQueries(updated);
        localStorage.setItem(STORAGE_KEY, JSON.stringify(updated));
        setReplyingTo(null);
        setReplyText('');
        alert(t('queries.reply_sent'));
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
                    <Tabs defaultActiveKey="pending" className="mb-4">
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
