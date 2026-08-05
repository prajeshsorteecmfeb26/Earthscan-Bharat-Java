const http = require('http');
const url = require('url');
const fs = require('fs');
const path = require('path');

const PORT = 8080;
const USERS_FILE = path.join(__dirname, 'mock-users.json');

const defaultUsers = [
  { id: 1, name: 'Rajesh Kumar', email: 'farmer@earthscan.in', role: 'Farmer' },
  { id: 2, name: 'Priya Sharma', email: 'buyer@earthscan.in', role: 'Land Buyer' },
  { id: 3, name: 'Dr. Anita Desai', email: 'expert@earthscan.in', role: 'Agriculture Expert' },
  { id: 4, name: 'Admin User', email: 'admin@earthscan.in', role: 'Admin' },
  { id: 5, name: 'Shraddha Dabade', email: 'shraddha.dabade.cmfeb26@gmail.com', role: 'Agriculture Expert' },
];

let users = [];

function loadUsers() {
  if (fs.existsSync(USERS_FILE)) {
    try {
      const data = fs.readFileSync(USERS_FILE, 'utf8');
      users = JSON.parse(data);
    } catch (e) {
      users = [...defaultUsers];
    }
  } else {
    users = [...defaultUsers];
    saveUsers();
  }
  users.forEach(u => {
    if (!u.token) u.token = 'mock-jwt-token-' + u.id;
  });
}

function saveUsers() {
  try {
    fs.writeFileSync(USERS_FILE, JSON.stringify(users, null, 2), 'utf8');
  } catch (e) {
    console.error('Error saving users file:', e);
  }
}

loadUsers();

const SAVED_SEARCHES_FILE = path.join(__dirname, 'mock-saved-searches.json');

const defaultSavedSearches = [
  {
    id: 1,
    label: 'Fertile Black Soil Agricultural Land',
    locationQuery: 'Sinnar Taluka, Nashik',
    soilTypeName: 'Black Cotton',
    landId: 1,
    notifyOnMatch: true,
    createdAt: new Date().toISOString()
  }
];

let savedSearches = [];

function loadSavedSearches() {
  if (fs.existsSync(SAVED_SEARCHES_FILE)) {
    try {
      const data = fs.readFileSync(SAVED_SEARCHES_FILE, 'utf8');
      savedSearches = JSON.parse(data);
    } catch (e) {
      savedSearches = [...defaultSavedSearches];
    }
  } else {
    savedSearches = [...defaultSavedSearches];
    saveSavedSearches();
  }
}

function saveSavedSearches() {
  try {
    fs.writeFileSync(SAVED_SEARCHES_FILE, JSON.stringify(savedSearches, null, 2), 'utf8');
  } catch (e) {
    console.error('Error saving saved searches file:', e);
  }
}

loadSavedSearches();

const CONTACT_QUERIES_FILE = path.join(__dirname, 'mock-contact-queries.json');
let contactQueries = [
  {
    id: 'cq-1',
    name: 'Suresh Patil',
    email: 'suresh.patil@gmail.com',
    message: 'Need assistance with soil fertility test report for sugarcane crop in Sangli.',
    status: 'Pending',
    reply: '',
    createdAt: new Date(Date.now() - 86400000).toISOString()
  }
];

function loadContactQueries() {
  if (fs.existsSync(CONTACT_QUERIES_FILE)) {
    try {
      const data = fs.readFileSync(CONTACT_QUERIES_FILE, 'utf8');
      contactQueries = JSON.parse(data);
    } catch (e) {
      saveContactQueries();
    }
  } else {
    saveContactQueries();
  }
}

function saveContactQueries() {
  try {
    fs.writeFileSync(CONTACT_QUERIES_FILE, JSON.stringify(contactQueries, null, 2), 'utf8');
  } catch (e) {
    console.error('Error saving contact queries file:', e);
  }
}

loadContactQueries();

const soilTypes = ['Black Cotton', 'Red Loam', 'Alluvial', 'Laterite', 'Clay Loam'];
const districts = ['Nashik', 'Pune', 'Nagpur', 'Bengaluru Rural', 'Coimbatore', 'Gondia', 'Jalgaon', 'Sangli', 'Latur', 'Solapur', 'Ratnagiri', 'Aurangabad'];

const lands = [
  {
    id: 1,
    title: 'Fertile Black Soil Agricultural Land',
    district: 'Nashik',
    state: 'Maharashtra',
    price: 2800000,
    sizeInAcres: 5.2,
    areaAcres: 5.2,
    soilType: 'Black Cotton',
    waterSource: 'Borewell & Canal',
    groundwaterLevelDepth: 25,
    irrigationAvailable: true,
    landIntelligenceScore: 89,
    borewellSuccessProbability: 93,
    status: 'AVAILABLE',
    verified: true,
    ownerEmail: 'farmer@earthscan.in',
    description: 'High-yield black soil land ideal for sugarcane, grape orchards, and onions. Dual water connectivity.',
    location: 'Sinnar Taluka, Nashik',
    imageUrl: 'https://images.unsplash.com/photo-1500382017468-9049fed747ef?w=800&q=80'
  },
  {
    id: 2,
    title: 'Riverfront Organic Cultivation Plot',
    district: 'Pune',
    state: 'Maharashtra',
    price: 4500000,
    sizeInAcres: 7.5,
    areaAcres: 7.5,
    soilType: 'Alluvial',
    waterSource: 'River Pump',
    groundwaterLevelDepth: 18,
    irrigationAvailable: true,
    landIntelligenceScore: 94,
    borewellSuccessProbability: 88,
    status: 'AVAILABLE',
    verified: true,
    ownerEmail: 'farmer@earthscan.in',
    description: 'Rich alluvial soil adjacent to Mutha river canal. Certified organic history for past 4 years.',
    location: 'Haveli, Pune',
    imageUrl: 'https://images.unsplash.com/photo-1625246333195-78d9c38ad449?w=800&q=80'
  },
  {
    id: 3,
    title: 'Red Loam Soil Fruit Orchard Plot',
    district: 'Bengaluru Rural',
    state: 'Karnataka',
    price: 3600000,
    sizeInAcres: 3.8,
    areaAcres: 3.8,
    soilType: 'Red Loam',
    waterSource: 'Borewell',
    groundwaterLevelDepth: 65,
    irrigationAvailable: true,
    landIntelligenceScore: 82,
    borewellSuccessProbability: 79,
    status: 'AVAILABLE',
    verified: true,
    ownerEmail: 'buyer@earthscan.in',
    description: 'Suitable for mango and pomegranate plantations. Fenced with solar perimeter.',
    location: 'Doddaballapura, Bengaluru Rural',
    imageUrl: 'https://images.unsplash.com/photo-1592982537447-6f2a6a0c7c18?w=800&q=80'
  },
  {
    id: 4,
    title: 'High-Yield Rice & Sugarcane Belt',
    district: 'Gondia',
    state: 'Maharashtra',
    price: 3200000,
    sizeInAcres: 6.0,
    areaAcres: 6.0,
    soilType: 'Clay Loam',
    waterSource: 'Canal & Well',
    groundwaterLevelDepth: 35,
    irrigationAvailable: true,
    landIntelligenceScore: 86,
    borewellSuccessProbability: 85,
    status: 'AVAILABLE',
    verified: true,
    ownerEmail: 'farmer@earthscan.in',
    description: 'Highly fertile paddy and cash-crop agricultural land with abundant canal water supply.',
    location: 'Tirora, Gondia',
    imageUrl: 'https://images.unsplash.com/photo-1500382017468-9049fed747ef?w=800&q=80'
  },
  {
    id: 5,
    title: 'Orange & Cotton Farmland',
    district: 'Nagpur',
    state: 'Maharashtra',
    price: 5100000,
    sizeInAcres: 10.0,
    areaAcres: 10.0,
    soilType: 'Black Cotton',
    waterSource: 'Deep Borewell',
    groundwaterLevelDepth: 55,
    irrigationAvailable: true,
    landIntelligenceScore: 91,
    borewellSuccessProbability: 92,
    status: 'AVAILABLE',
    verified: true,
    ownerEmail: 'farmer@earthscan.in',
    description: 'Prime Vidarbha agricultural land optimized for citrus orchards and cotton farming.',
    location: 'Kalameshwar, Nagpur',
    imageUrl: 'https://images.unsplash.com/photo-1625246333195-78d9c38ad449?w=800&q=80'
  },
  {
    id: 6,
    title: 'Coconut & Spices Plantation',
    district: 'Coimbatore',
    state: 'Tamil Nadu',
    price: 6200000,
    sizeInAcres: 8.2,
    areaAcres: 8.2,
    soilType: 'Red Loam',
    waterSource: 'Drip Irrigation System',
    groundwaterLevelDepth: 40,
    irrigationAvailable: true,
    landIntelligenceScore: 88,
    borewellSuccessProbability: 84,
    status: 'AVAILABLE',
    verified: true,
    ownerEmail: 'buyer@earthscan.in',
    description: 'Fully equipped coconut plantation with automated drip network and solar fencing.',
    location: 'Pollachi, Coimbatore',
    imageUrl: 'https://images.unsplash.com/photo-1592982537447-6f2a6a0c7c18?w=800&q=80'
  },
  {
    id: 7,
    title: 'Grape Vineyard & Onion Cultivation Land',
    district: 'Jalgaon',
    state: 'Maharashtra',
    price: 3900000,
    sizeInAcres: 6.5,
    areaAcres: 6.5,
    soilType: 'Black Cotton',
    waterSource: 'Borewell & Drip',
    groundwaterLevelDepth: 28,
    irrigationAvailable: true,
    landIntelligenceScore: 92,
    borewellSuccessProbability: 90,
    status: 'AVAILABLE',
    verified: true,
    ownerEmail: 'farmer@earthscan.in',
    description: 'Fertile black soil land with high mineral content, optimized for onion, banana, and grape cultivation.',
    location: 'Raver, Jalgaon',
    imageUrl: 'https://images.unsplash.com/photo-1500382017468-9049fed747ef?w=800&q=80'
  },
  {
    id: 8,
    title: 'Turmeric & Sugarcane Belt Farmland',
    district: 'Sangli',
    state: 'Maharashtra',
    price: 4800000,
    sizeInAcres: 5.0,
    areaAcres: 5.0,
    soilType: 'Alluvial',
    waterSource: 'Krishna River Lift Irrigation',
    groundwaterLevelDepth: 15,
    irrigationAvailable: true,
    landIntelligenceScore: 95,
    borewellSuccessProbability: 96,
    status: 'AVAILABLE',
    verified: true,
    ownerEmail: 'farmer@earthscan.in',
    description: 'Premium riverbank agricultural land with perpetual water security for high-value cash crops.',
    location: 'Walwa, Sangli',
    imageUrl: 'https://images.unsplash.com/photo-1625246333195-78d9c38ad449?w=800&q=80'
  },
  {
    id: 9,
    title: 'Soybean & Pulse Agro Plantation',
    district: 'Latur',
    state: 'Maharashtra',
    price: 3100000,
    sizeInAcres: 8.5,
    areaAcres: 8.5,
    soilType: 'Black Cotton',
    waterSource: 'Deep Borewell',
    groundwaterLevelDepth: 48,
    irrigationAvailable: true,
    landIntelligenceScore: 87,
    borewellSuccessProbability: 82,
    status: 'AVAILABLE',
    verified: true,
    ownerEmail: 'farmer@earthscan.in',
    description: 'Expansive black cotton soil plot suitable for pulses, soybean, and solar agri-farming.',
    location: 'Ausa, Latur',
    imageUrl: 'https://images.unsplash.com/photo-1592982537447-6f2a6a0c7c18?w=800&q=80'
  },
  {
    id: 10,
    title: 'Pomegranate & Grape Belt Estate',
    district: 'Solapur',
    state: 'Maharashtra',
    price: 5800000,
    sizeInAcres: 12.0,
    areaAcres: 12.0,
    soilType: 'Clay Loam',
    waterSource: 'Canal & Well Storage',
    groundwaterLevelDepth: 32,
    irrigationAvailable: true,
    landIntelligenceScore: 89,
    borewellSuccessProbability: 87,
    status: 'AVAILABLE',
    verified: true,
    ownerEmail: 'farmer@earthscan.in',
    description: 'High-income export-quality pomegranate farm with drip irrigation grid.',
    location: 'Pandharpur, Solapur',
    imageUrl: 'https://images.unsplash.com/photo-1500382017468-9049fed747ef?w=800&q=80'
  },
  {
    id: 11,
    title: 'Alphonso Mango & Cashew Grove',
    district: 'Ratnagiri',
    state: 'Maharashtra',
    price: 6500000,
    sizeInAcres: 9.0,
    areaAcres: 9.0,
    soilType: 'Laterite',
    waterSource: 'Natural Springs & Well',
    groundwaterLevelDepth: 22,
    irrigationAvailable: true,
    landIntelligenceScore: 93,
    borewellSuccessProbability: 91,
    status: 'AVAILABLE',
    verified: true,
    ownerEmail: 'farmer@earthscan.in',
    description: 'Coastal Konkan laterite soil plantation ideal for premium Alphonso mangoes and cashew nuts.',
    location: 'Guhagar, Ratnagiri',
    imageUrl: 'https://images.unsplash.com/photo-1625246333195-78d9c38ad449?w=800&q=80'
  },
  {
    id: 12,
    title: 'High-Yield Cotton & Maize Agricultural Plot',
    district: 'Aurangabad',
    state: 'Maharashtra',
    price: 4200000,
    sizeInAcres: 7.2,
    areaAcres: 7.2,
    soilType: 'Black Cotton',
    waterSource: 'Jayakwadi Canal System',
    groundwaterLevelDepth: 38,
    irrigationAvailable: true,
    landIntelligenceScore: 88,
    borewellSuccessProbability: 86,
    status: 'AVAILABLE',
    verified: true,
    ownerEmail: 'farmer@earthscan.in',
    description: 'Fertile Marathwada agricultural plot with canal irrigation for double-crop seasonal rotation.',
    location: 'Paithan, Aurangabad',
    imageUrl: 'https://images.unsplash.com/photo-1592982537447-6f2a6a0c7c18?w=800&q=80'
  }
];

const categories = ['Crop Disease', 'Soil & Water', 'Government Schemes', 'Market Prices', 'Borewell Advisory'];

const forumPosts = [
  {
    id: '66a0f1e29c1234567890abcd',
    title: 'Best irrigation strategy for black soil sugarcane during summer?',
    content: 'We have 5 acres of black soil in Nashik. Looking for expert tips on drip vs furrow irrigation efficiency.',
    authorName: 'Rajesh Kumar',
    authorRole: 'Farmer',
    category: 'Soil & Water',
    resolved: true,
    createdAt: new Date().toISOString(),
    comments: [
      {
        id: 'c1',
        authorName: 'Dr. Anita Desai',
        authorRole: 'Agriculture Expert',
        content: 'Sub-surface drip irrigation is recommended for black cotton soil to prevent waterlogging while keeping root zones optimal.',
        createdAt: new Date().toISOString()
      }
    ]
  },
  {
    id: '66a0f1e29c1234567890abce',
    title: 'How to calculate Borewell Success Rate using EarthScan layers?',
    content: 'Interested in acquiring land in Pune district. How accurate is the 250m water table depth mapping?',
    authorName: 'Priya Sharma',
    authorRole: 'Land Buyer',
    category: 'Borewell Advisory',
    resolved: false,
    createdAt: new Date().toISOString(),
    comments: []
  }
];

const notifications = [
  { id: 'n1', title: 'Land Score Updated', message: 'Your listing in Nashik achieved Land Intelligence Score 89/100.', read: false, createdAt: new Date().toISOString() },
  { id: 'n2', title: 'New Agronomist Reply', message: 'Dr. Anita Desai replied to your post on Irrigation Strategy.', read: false, createdAt: new Date().toISOString() }
];

const server = http.createServer((req, res) => {
  // CORS Headers
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, PUT, PATCH, DELETE, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');

  if (req.method === 'OPTIONS') {
    res.writeHead(200);
    res.end();
    return;
  }

  const parsedUrl = url.parse(req.url, true);
  const pathname = parsedUrl.pathname;
  const method = req.method;

  let bodyData = '';
  req.on('data', chunk => { bodyData += chunk; });
  req.on('end', () => {
    let body = {};
    if (bodyData) {
      try { 
        body = JSON.parse(bodyData); 
      } catch (e) {
        try {
          body = Object.fromEntries(new URLSearchParams(bodyData));
        } catch (e2) {}
      }
    }

    // Helper: Find current user by auth header token
    const authHeader = req.headers['authorization'] || req.headers['Authorization'] || '';
    const tokenStr = String(authHeader).replace(/^Bearer\s+/i, '').trim();
    let currentUser = users.find(u => u.token === tokenStr || ('mock-jwt-token-' + u.id) === tokenStr);
    if (!currentUser && tokenStr.startsWith('mock-jwt-token-')) {
      const targetId = parseInt(tokenStr.replace('mock-jwt-token-', ''));
      currentUser = users.find(u => u.id === targetId);
    }
    if (!currentUser) {
      currentUser = users[0];
    }

    // Root landing page guide
    if (method === 'GET' && (pathname === '/' || pathname === '')) {
      res.writeHead(200, { 'Content-Type': 'text/html' });
      res.end(`
        <!DOCTYPE html>
        <html>
          <head>
            <title>EarthScan Bharat API Gateway</title>
            <meta charset="utf-8">
            <style>
              body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background-color: #0a0f18; color: #ffffff; display: flex; align-items: center; justify-content: center; height: 100vh; margin: 0; }
              .card { background: rgba(255, 255, 255, 0.05); border: 1px solid rgba(255, 255, 255, 0.1); border-radius: 16px; padding: 40px; text-align: center; max-width: 500px; }
              h1 { color: #00e676; margin-bottom: 10px; font-size: 28px; }
              p { color: #94a3b8; font-size: 16px; line-height: 1.5; margin-bottom: 30px; }
              .btn { display: inline-block; padding: 12px 24px; border-radius: 30px; font-weight: bold; text-decoration: none; transition: transform 0.2s; margin: 5px; }
              .btn-primary { background: linear-gradient(90deg, #2979ff, #1c54b2); color: white; }
              .btn-secondary { background: rgba(255, 255, 255, 0.1); color: white; border: 1px solid rgba(255, 255, 255, 0.2); }
            </style>
          </head>
          <body>
            <div class="card">
              <h1>✦ EarthScan Bharat API Gateway</h1>
              <p>Port 8080 hosts the backend REST API services.<br/>To open the main interactive application, click below:</p>
              <div>
                <a href="http://localhost:5173" class="btn btn-primary">Open Web Application (Port 5173)</a>
                <a href="/swagger-ui.html" class="btn btn-secondary">API Docs</a>
              </div>
            </div>
          </body>
        </html>
      `);
      return;
    }

    // Auth API
    if (method === 'POST' && pathname === '/api/auth/login') {
      const emailInput = (body.email || '').toLowerCase().trim();
      const matchedUser = users.find(u => u.email.toLowerCase() === emailInput);
      
      if (!matchedUser) {
        res.writeHead(401, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ message: 'Invalid email or password. Please register an account first.' }));
        return;
      }

      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({
        token: matchedUser.token || ('mock-jwt-token-' + matchedUser.id),
        user: matchedUser
      }));
      return;
    }

    if (method === 'POST' && pathname === '/api/auth/register') {
      const roleMap = {
        'FARMER': 'Farmer',
        'LAND_BUYER': 'Land Buyer',
        'BUYER': 'Land Buyer',
        'AGRICULTURE_EXPERT': 'Agriculture Expert',
        'EXPERT': 'Agriculture Expert',
        'ADMIN': 'Admin'
      };
      const formattedRole = roleMap[(body.role || '').toUpperCase()] || body.role || 'Farmer';
      
      const existingUser = users.find(u => u.email.toLowerCase() === (body.email || '').toLowerCase().trim());
      if (existingUser) {
        existingUser.name = body.name || existingUser.name;
        existingUser.role = formattedRole;
        saveUsers();
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ message: 'User updated successfully', user: existingUser }));
        return;
      }

      const maxId = users.reduce((max, u) => (u.id > max ? u.id : max), 0);
      const newUser = {
        id: maxId + 1,
        name: body.name || 'New User',
        email: (body.email || 'user@earthscan.in').toLowerCase().trim(),
        role: formattedRole,
        token: 'mock-jwt-token-' + (maxId + 1)
      };
      users.push(newUser);
      saveUsers();
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ message: 'User registered successfully', user: newUser }));
      return;
    }

    if (method === 'GET' && pathname === '/api/auth/me') {
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify(currentUser));
      return;
    }

    if (method === 'POST' && pathname === '/api/auth/reset-password') {
      const email = (body.email || '').trim();
      const newPassword = body.newPassword || '';

      if (!email || !/^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(email)) {
        res.writeHead(400, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ message: 'Valid email address is required' }));
        return;
      }

      if (!newPassword) {
        res.writeHead(400, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ message: 'Password is required' }));
        return;
      }

      if (newPassword.length < 8) {
        res.writeHead(400, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ message: 'Password must be at least 8 characters' }));
        return;
      }

      if (newPassword.length > 72) {
        res.writeHead(400, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ message: 'Password must not exceed 72 characters' }));
        return;
      }

      if (!/[A-Za-z]/.test(newPassword)) {
        res.writeHead(400, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ message: 'Password must contain at least one letter' }));
        return;
      }

      if (!/\d/.test(newPassword)) {
        res.writeHead(400, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ message: 'Password must contain at least one digit' }));
        return;
      }

      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ message: 'Password reset successfully' }));
      return;
    }

    // Land API
    if (method === 'GET' && pathname === '/api/lands') {
      const q = (parsedUrl.query.q || '').toLowerCase().trim();
      const district = (parsedUrl.query.district || '').toLowerCase().trim();
      const soilType = (parsedUrl.query.soilType || '').toLowerCase().trim();
      const status = (parsedUrl.query.status || '').toLowerCase().trim();
      const minPrice = parseFloat(parsedUrl.query.minPrice);
      const maxPrice = parseFloat(parsedUrl.query.maxPrice);

      let filtered = lands.filter(l => {
        if (q) {
          const matchQ = (l.title || '').toLowerCase().includes(q) ||
                         (l.district || '').toLowerCase().includes(q) ||
                         (l.state || '').toLowerCase().includes(q) ||
                         (l.location || '').toLowerCase().includes(q) ||
                         (l.description || '').toLowerCase().includes(q) ||
                         (l.soilType || '').toLowerCase().includes(q);
          if (!matchQ) return false;
        }
        if (district && district !== 'all') {
          if ((l.district || '').toLowerCase() !== district) return false;
        }
        if (soilType && soilType !== 'all') {
          if ((l.soilType || '').toLowerCase() !== soilType) return false;
        }
        if (status && status !== 'all') {
          if ((l.status || '').toLowerCase() !== status) return false;
        }
        if (!isNaN(minPrice) && l.price < minPrice) return false;
        if (!isNaN(maxPrice) && l.price > maxPrice) return false;
        return true;
      });

      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({
        content: filtered,
        totalElements: filtered.length,
        totalPages: Math.ceil(filtered.length / 12) || 1,
        number: 0,
        size: 12
      }));
      return;
    }

    if (method === 'GET' && pathname === '/api/lands/soil-types') {
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify(soilTypes));
      return;
    }

    if (method === 'GET' && pathname === '/api/lands/districts') {
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify(districts));
      return;
    }

    if (method === 'GET' && pathname === '/api/lands/mine') {
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify(lands));
      return;
    }

    const landMatch = pathname.match(/^\/api\/lands\/(\d+)$/);
    if (method === 'GET' && landMatch) {
      const landId = parseInt(landMatch[1]);
      const item = lands.find(l => l.id === landId) || lands[0];
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify(item));
      return;
    }

    const landAnalysisMatch = pathname.match(/^\/api\/lands\/(\d+)\/analysis$/);
    if (method === 'GET' && landAnalysisMatch) {
      const landId = parseInt(landAnalysisMatch[1]);
      const item = lands.find(l => l.id === landId) || lands[0];
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({
        landId: item.id,
        landIntelligenceScore: item.landIntelligenceScore,
        borewellSuccessProbability: item.borewellSuccessProbability,
        nitrogenPpm: 145,
        phosphorusPpm: 38,
        potassiumPpm: 210,
        pHLevel: 7.2,
        organicCarbonPct: 0.78,
        groundwaterDepthMeters: 42,
        recommendedCrops: ['Sugarcane', 'Cotton', 'Grapes', 'Pomegranate', 'Soybean'],
        solarIrrigationSuitabilityScore: 91
      }));
      return;
    }

    if (method === 'POST' && pathname === '/api/lands') {
      const newLand = {
        id: lands.length + 1,
        ...body,
        landIntelligenceScore: 85,
        borewellSuccessProbability: 90,
        status: 'AVAILABLE',
        verified: true,
        imageUrl: 'https://images.unsplash.com/photo-1500382017468-9049fed747ef?w=800&q=80'
      };
      lands.push(newLand);
      res.writeHead(201, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify(newLand));
      return;
    }

    // Forum API
    if (method === 'GET' && (pathname === '/api/forum/posts' || pathname === '/api/forum/posts/feed' || pathname === '/api/forum/posts/unanswered')) {
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({
        content: forumPosts,
        totalElements: forumPosts.length,
        totalPages: 1,
        number: 0
      }));
      return;
    }

    if (method === 'GET' && pathname === '/api/forum/categories') {
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify(categories));
      return;
    }

    const postMatch = pathname.match(/^\/api\/forum\/posts\/([a-zA-Z0-9]+)$/);
    if (method === 'GET' && postMatch) {
      const post = forumPosts.find(p => p.id === postMatch[1]) || forumPosts[0];
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify(post));
      return;
    }

    if (method === 'POST' && pathname === '/api/forum/posts') {
      const newPost = {
        id: '66a0f1e29c1234567890' + Date.now().toString(16).slice(-4),
        title: body.title,
        content: body.content,
        authorName: currentUser.name,
        authorRole: currentUser.role,
        category: body.category || 'General',
        resolved: false,
        createdAt: new Date().toISOString(),
        comments: []
      };
      forumPosts.push(newPost);
      res.writeHead(201, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify(newPost));
      return;
    }

    // Forum Comments API
    const commentMatch = pathname.match(/^\/api\/forum\/posts\/([a-zA-Z0-9]+)\/comments$/);
    if (method === 'POST' && commentMatch) {
      const postId = commentMatch[1];
      const post = forumPosts.find(p => p.id === postId);
      const newComment = {
        id: 'c' + Date.now().toString(16),
        authorName: currentUser.name || 'Admin User',
        authorRole: currentUser.role || 'Admin',
        content: body.content || '',
        createdAt: new Date().toISOString()
      };
      if (post) {
        if (!post.comments) post.comments = [];
        post.comments.push(newComment);
      }
      res.writeHead(201, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify(newComment));
      return;
    }

    const delCommentMatch = pathname.match(/^\/api\/forum\/posts\/([a-zA-Z0-9]+)\/comments\/([a-zA-Z0-9]+)$/);
    if (method === 'DELETE' && delCommentMatch) {
      const postId = delCommentMatch[1];
      const commentId = delCommentMatch[2];
      const post = forumPosts.find(p => p.id === postId);
      if (post && post.comments) {
        post.comments = post.comments.filter(c => c.id !== commentId);
      }
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ message: 'Comment deleted successfully' }));
      return;
    }

    const resolvedMatch = pathname.match(/^\/api\/forum\/posts\/([a-zA-Z0-9]+)\/resolved$/);
    if (method === 'PATCH' && resolvedMatch) {
      const postId = resolvedMatch[1];
      const post = forumPosts.find(p => p.id === postId);
      if (post) {
        post.resolved = parsedUrl.query.resolved === 'true';
      }
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify(post || { message: 'Updated' }));
      return;
    }

    const delPostMatch = pathname.match(/^\/api\/forum\/posts\/([a-zA-Z0-9]+)$/);
    if (method === 'DELETE' && delPostMatch) {
      const postId = delPostMatch[1];
      const idx = forumPosts.findIndex(p => p.id === postId);
      if (idx !== -1) {
        forumPosts.splice(idx, 1);
      }
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ message: 'Post deleted successfully' }));
      return;
    }

    // Notifications API
    if (method === 'GET' && pathname === '/api/notifications') {
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ content: notifications, unreadCount: notifications.filter(n => !n.read).length }));
      return;
    }

    if (method === 'GET' && pathname === '/api/notifications/unread-count') {
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ count: notifications.filter(n => !n.read).length }));
      return;
    }

    // Saved searches API
    if (method === 'GET' && pathname === '/api/saved-searches') {
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify(savedSearches));
      return;
    }

    if (method === 'POST' && pathname === '/api/saved-searches') {
      const existing = savedSearches.find(s => 
        (body.landId && s.landId === body.landId) || 
        (body.label && s.label.toLowerCase() === body.label.toLowerCase())
      );
      if (existing) {
        res.writeHead(409, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ message: 'Search query or land listing already saved.' }));
        return;
      }

      const newSaved = {
        id: Date.now(),
        label: body.label || 'Saved Property',
        locationQuery: body.locationQuery || 'Location',
        soilTypeName: body.soilTypeName || 'Soil',
        landId: body.landId || null,
        notifyOnMatch: body.notifyOnMatch ?? true,
        createdAt: new Date().toISOString()
      };
      savedSearches.push(newSaved);
      saveSavedSearches();
      res.writeHead(201, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify(newSaved));
      return;
    }

    if (method === 'DELETE' && pathname.startsWith('/api/saved-searches/')) {
      const idStr = pathname.split('/')[3];
      const idx = savedSearches.findIndex(s => String(s.id) === idStr);
      if (idx !== -1) {
        savedSearches.splice(idx, 1);
        saveSavedSearches();
      }
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ message: 'Deleted successfully' }));
      return;
    }

    // Contact Us API
    if (method === 'POST' && (pathname === '/api/contact' || pathname === '/api/contact-queries')) {
      const newQuery = {
        id: 'cq-' + Date.now().toString(16),
        name: body.name || 'User',
        email: body.email || '',
        message: body.message || '',
        status: 'Pending',
        reply: '',
        createdAt: new Date().toISOString()
      };
      contactQueries.unshift(newQuery);
      saveContactQueries();
      res.writeHead(201, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify(newQuery));
      return;
    }

    if (method === 'GET' && (pathname === '/api/admin/contact-queries' || pathname === '/api/contact-queries')) {
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify(contactQueries));
      return;
    }

    if (method === 'PUT' && pathname.match(/^\/api\/admin\/contact-queries\/([a-zA-Z0-9\-]+)\/reply$/)) {
      const queryId = pathname.split('/')[4];
      const query = contactQueries.find(q => String(q.id) === String(queryId));
      if (query) {
        query.reply = body.reply || '';
        query.status = 'Answered';
        query.repliedAt = new Date().toISOString();
        saveContactQueries();
      }
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify(query || { message: 'Updated' }));
      return;
    }

    if (method === 'DELETE' && pathname.match(/^\/api\/(admin\/)?contact-queries\/([a-zA-Z0-9\-]+)$/)) {
      const idStr = pathname.split('/').pop();
      const idx = contactQueries.findIndex(q => String(q.id) === String(idStr));
      if (idx !== -1) {
        contactQueries.splice(idx, 1);
        saveContactQueries();
      }
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ message: 'Deleted successfully' }));
      return;
    }

    // Admin API
    if (method === 'GET' && pathname === '/api/admin/stats') {
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({
        totalUsers: 1420,
        totalLands: 385,
        totalForumPosts: 512,
        verifiedLands: 310,
        averageLandScore: 86.4,
        averageBorewellSuccessRate: 84.1
      }));
      return;
    }

    if (method === 'GET' && pathname === '/api/admin/users') {
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify(users));
      return;
    }

    if (method === 'GET' && pathname === '/api/admin/users/page') {
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ content: users, totalElements: users.length, totalPages: 1 }));
      return;
    }

    if (method === 'PUT' && pathname.startsWith('/api/admin/users/')) {
      const id = parseInt(pathname.split('/')[4]);
      const user = users.find(u => u.id === id);
      if (user && body.role) {
        user.role = body.role;
        saveUsers();
      }
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify(user || { message: 'Updated' }));
      return;
    }

    if (method === 'DELETE' && pathname.startsWith('/api/admin/users/')) {
      const id = parseInt(pathname.split('/')[4]);
      const idx = users.findIndex(u => u.id === id);
      if (idx !== -1) {
        const deletedUser = users[idx];
        users.splice(idx, 1);
        saveUsers();

        // 1. Cascading deletion of user's forum posts
        for (let i = forumPosts.length - 1; i >= 0; i--) {
          const p = forumPosts[i];
          if (
            (deletedUser.name && p.authorName && p.authorName.toLowerCase() === deletedUser.name.toLowerCase()) ||
            (deletedUser.email && p.authorEmail && p.authorEmail.toLowerCase() === deletedUser.email.toLowerCase())
          ) {
            forumPosts.splice(i, 1);
          }
        }

        // 2. Cascading deletion of user's comments across all remaining forum posts
        forumPosts.forEach(post => {
          if (Array.isArray(post.comments)) {
            post.comments = post.comments.filter(c => {
              const matchName = deletedUser.name && c.authorName && c.authorName.toLowerCase() === deletedUser.name.toLowerCase();
              const matchEmail = deletedUser.email && c.authorEmail && c.authorEmail.toLowerCase() === deletedUser.email.toLowerCase();
              return !matchName && !matchEmail;
            });
          }
        });

        // 3. Cascading deletion of user's land listings
        for (let i = lands.length - 1; i >= 0; i--) {
          const l = lands[i];
          if (
            (deletedUser.email && l.ownerEmail && l.ownerEmail.toLowerCase() === deletedUser.email.toLowerCase()) ||
            (deletedUser.id && l.ownerId === deletedUser.id)
          ) {
            lands.splice(i, 1);
          }
        }

        // 4. Cascading deletion of user's saved searches
        for (let i = savedSearches.length - 1; i >= 0; i--) {
          const s = savedSearches[i];
          if (
            (deletedUser.email && s.userEmail && s.userEmail.toLowerCase() === deletedUser.email.toLowerCase()) ||
            (deletedUser.id && s.userId === deletedUser.id)
          ) {
            savedSearches.splice(i, 1);
          }
        }
        saveSavedSearches();
      }
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ message: 'User and all associated data deleted successfully' }));
      return;
    }

    // Fallback 200 empty array/object to prevent breaking React UI
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify([]));
  });
});

server.listen(PORT, () => {
  console.log(`EarthScan Bharat Mock Backend Server listening on http://localhost:${PORT}`);
});
