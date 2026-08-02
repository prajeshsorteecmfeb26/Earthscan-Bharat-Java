export const normalizeRole = (r) => {
    if (!r) return '';
    const u = String(r).toUpperCase().replace(/\s+/g, '_');
    if (u === 'FARMER') return 'Farmer';
    if (u === 'LAND_BUYER' || u === 'LANDBUYER' || u === 'BUYER') return 'Land Buyer';
    if (u === 'AGRICULTURE_EXPERT' || u === 'EXPERT') return 'Agriculture Expert';
    if (u === 'ADMIN') return 'Admin';
    return r;
};
