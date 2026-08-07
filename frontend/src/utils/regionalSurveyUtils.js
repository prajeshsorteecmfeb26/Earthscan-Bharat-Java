/**
 * Utility for fetching dynamic hydro-geological, climate, elevation, and soil data
 * for any global or Indian city/location using live open Web APIs (Open-Meteo & ISRIC SoilGrids).
 */

async function fetchWithTimeout(url, options = {}, timeoutMs = 3500) {
    const controller = new AbortController();
    const id = setTimeout(() => controller.abort(), timeoutMs);
    try {
        const response = await fetch(url, { ...options, signal: controller.signal });
        clearTimeout(id);
        return response;
    } catch (err) {
        clearTimeout(id);
        throw err;
    }
}

/**
 * Fetches and computes dynamic regional survey metrics for given coordinates & location info.
 */
export async function fetchRegionalSurveyData(lat, lng, locationName = '', addressObj = {}) {
    let elevation = 560; // default elevation in meters (Pune baseline)
    let annualRainfall = 740; // default annual rainfall in mm
    let clayPct = null;
    let sandPct = null;
    let siltPct = null;

    // 1. Fetch Elevation from Open-Meteo Elevation API
    try {
        const elevUrl = `https://api.open-meteo.com/v1/elevation?latitude=${lat}&longitude=${lng}`;
        const res = await fetchWithTimeout(elevUrl, {}, 3000);
        const data = await res.json();
        if (data && Array.isArray(data.elevation) && data.elevation.length > 0) {
            elevation = Math.round(data.elevation[0]);
        }
    } catch (e) {
        console.warn('Open-Meteo Elevation API request skipped/failed:', e);
    }

    // 2. Fetch Precipitation (Annual Rainfall) from Open-Meteo APIs
    try {
        const precUrl = `https://api.open-meteo.com/v1/forecast?latitude=${lat}&longitude=${lng}&daily=precipitation_sum&timezone=auto&past_days=92`;
        const res = await fetchWithTimeout(precUrl, {}, 3000);
        const data = await res.json();
        if (data?.daily?.precipitation_sum && Array.isArray(data.daily.precipitation_sum)) {
            const sum92 = data.daily.precipitation_sum.reduce((a, b) => a + (b || 0), 0);
            annualRainfall = Math.round(sum92 * 3.8);
        }

        // Try Archive API for exact 1-year historical total
        const lastYear = new Date().getFullYear() - 1;
        const archUrl = `https://archive-api.open-meteo.com/v1/archive?latitude=${lat}&longitude=${lng}&start_date=${lastYear}-01-01&end_date=${lastYear}-12-31&daily=precipitation_sum`;
        const archRes = await fetchWithTimeout(archUrl, {}, 2500);
        const archData = await archRes.json();
        if (archData?.daily?.precipitation_sum && Array.isArray(archData.daily.precipitation_sum)) {
            const totalYear = archData.daily.precipitation_sum.reduce((a, b) => a + (b || 0), 0);
            if (totalYear > 50) {
                annualRainfall = Math.round(totalYear);
            }
        }
    } catch (e) {
        console.warn('Open-Meteo Precipitation API request skipped/failed:', e);
    }

    // 3. Fetch Soil Composition from ISRIC SoilGrids REST API
    try {
        const soilUrl = `https://rest.isric.org/soilgrids/v2.0/properties/query?lon=${lng}&lat=${lat}&property=clay&property=sand&property=silt&depth=0-5cm`;
        const res = await fetchWithTimeout(soilUrl, {}, 3000);
        const data = await res.json();
        if (data?.properties?.layers) {
            data.properties.layers.forEach(layer => {
                const meanVal = layer.depths?.[0]?.values?.mean;
                if (meanVal != null) {
                    const pct = Math.round(meanVal / 10);
                    if (layer.name === 'clay') clayPct = pct;
                    if (layer.name === 'sand') sandPct = pct;
                    if (layer.name === 'silt') siltPct = pct;
                }
            });
        }
    } catch (e) {
        console.warn('ISRIC SoilGrids API request skipped/failed:', e);
    }

    // 4. Derive Geographical Context & Soil Properties
    const locLower = (locationName + ' ' + (addressObj.state || '') + ' ' + (addressObj.state_district || '') + ' ' + (addressObj.county || '')).toLowerCase();

    let soilType = 'Black Cotton Soil';
    let waterRetention = 'High';
    let soilDrainage = 'Moderate';

    if (clayPct !== null && sandPct !== null) {
        if (sandPct > 55) {
            soilType = 'Sandy Loam Soil';
            waterRetention = 'Low';
            soilDrainage = 'Excessive';
        } else if (clayPct > 40) {
            soilType = 'Black Cotton Soil';
            waterRetention = 'High';
            soilDrainage = 'Moderate';
        } else if (siltPct > 40 || locLower.includes('uttar pradesh') || locLower.includes('bihar') || locLower.includes('punjab') || locLower.includes('haryana') || locLower.includes('delhi')) {
            soilType = 'Alluvial Soil';
            waterRetention = 'Moderate';
            soilDrainage = 'Well-Drained';
        } else {
            soilType = 'Red Loam Soil';
            waterRetention = 'Moderate';
            soilDrainage = 'Well-Drained';
        }
    } else {
        // Geographic rule engine for Indian regional soil mapping
        if (locLower.includes('maharashtra') || locLower.includes('pune') || locLower.includes('nashik') || locLower.includes('nagpur') || locLower.includes('solapur') || locLower.includes('aurangabad') || locLower.includes('satara') || locLower.includes('madhya pradesh') || locLower.includes('gujarat')) {
            soilType = 'Black Cotton Soil';
            waterRetention = 'High';
            soilDrainage = 'Moderate';
        } else if (locLower.includes('delhi') || locLower.includes('punjab') || locLower.includes('haryana') || locLower.includes('uttar pradesh') || locLower.includes('bihar') || locLower.includes('west bengal') || locLower.includes('kanpur') || locLower.includes('lucknow') || locLower.includes('patna')) {
            soilType = 'Alluvial Soil';
            waterRetention = 'Moderate';
            soilDrainage = 'Well-Drained';
        } else if (locLower.includes('rajasthan') || locLower.includes('jodhpur') || locLower.includes('bikaner') || locLower.includes('jaisalmer') || locLower.includes('barmer') || locLower.includes('desert') || locLower.includes('kutch')) {
            soilType = 'Sandy Arid Soil';
            waterRetention = 'Low';
            soilDrainage = 'Excessive';
        } else if (locLower.includes('konkan') || locLower.includes('goa') || locLower.includes('kerala') || locLower.includes('mangalore') || locLower.includes('ratnagiri')) {
            soilType = 'Laterite Soil';
            waterRetention = 'Moderate';
            soilDrainage = 'Well-Drained';
        } else if (elevation > 1100 || locLower.includes('himachal') || locLower.includes('uttarakhand') || locLower.includes('shimla') || locLower.includes('manali') || locLower.includes('jammu') || locLower.includes('kashmir') || locLower.includes('sikkim') || locLower.includes('meghalaya')) {
            soilType = 'Mountain Loam Soil';
            waterRetention = 'Moderate';
            soilDrainage = 'Good';
        } else if (locLower.includes('karnataka') || locLower.includes('bangalore') || locLower.includes('bengaluru') || locLower.includes('tamil nadu') || locLower.includes('chennai') || locLower.includes('andhra') || locLower.includes('telangana') || locLower.includes('hyderabad') || locLower.includes('odisha')) {
            soilType = 'Red Loam Soil';
            waterRetention = 'Moderate';
            soilDrainage = 'Well-Drained';
        } else {
            soilType = elevation < 50 ? 'Coastal Alluvial Soil' : 'Loam Soil';
            waterRetention = 'Moderate';
            soilDrainage = 'Well-Drained';
        }
    }

    // 5. Determine Groundwater Status & Borewell Depth
    let groundwaterStatus = 'Semi-Critical';
    let groundwaterVariant = 'text-warning';
    let borewellDepth = 120;

    if (locLower.includes('rajasthan') || locLower.includes('jodhpur') || locLower.includes('bikaner') || annualRainfall < 450) {
        groundwaterStatus = 'Over-Exploited';
        groundwaterVariant = 'text-danger';
        borewellDepth = Math.round(190 + (Math.abs(lat * 10) % 70));
    } else if (annualRainfall > 1600 || elevation < 30 || locLower.includes('kerala') || locLower.includes('goa') || locLower.includes('konkan') || locLower.includes('assam')) {
        groundwaterStatus = 'Safe';
        groundwaterVariant = 'text-success';
        borewellDepth = Math.round(35 + (elevation * 0.3) + (Math.abs(lng * 10) % 25));
    } else if (annualRainfall > 950 || locLower.includes('ganga') || locLower.includes('uttar pradesh') || locLower.includes('bihar')) {
        groundwaterStatus = 'Safe';
        groundwaterVariant = 'text-success';
        borewellDepth = Math.round(65 + (Math.abs(lat * 10) % 30));
    } else if (annualRainfall < 700 || locLower.includes('bangalore') || locLower.includes('hyderabad') || locLower.includes('anantapur') || locLower.includes('chennai')) {
        groundwaterStatus = 'Critical';
        groundwaterVariant = 'text-warning';
        borewellDepth = Math.round(155 + (Math.abs(lng * 10) % 55));
    } else {
        groundwaterStatus = 'Semi-Critical';
        groundwaterVariant = 'text-warning';
        borewellDepth = Math.round(110 + (Math.abs(lat * 7) % 35));
    }

    // 6. Determine Flood Risk Level
    let floodRisk = 'Low';
    let floodRiskVariant = 'text-success';

    if ((elevation < 15 && annualRainfall > 1200) || locLower.includes('mumbai') || locLower.includes('chennai') || locLower.includes('kolkata') || locLower.includes('patna') || locLower.includes('kerala')) {
        floodRisk = 'High';
        floodRiskVariant = 'text-danger';
    } else if (elevation < 40 || annualRainfall > 1400 || locLower.includes('assam') || locLower.includes('surat') || locLower.includes('delhi')) {
        floodRisk = 'Moderate';
        floodRiskVariant = 'text-warning';
    } else {
        floodRisk = 'Low';
        floodRiskVariant = 'text-success';
    }

    return {
        soilType,
        groundwaterStatus,
        groundwaterVariant,
        borewellDepth,
        floodRisk,
        floodRiskVariant,
        avgRainfall: Math.max(250, annualRainfall),
        waterRetention,
        soilDrainage,
        elevation
    };
}
