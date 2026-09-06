import type { VercelRequest, VercelResponse } from '@vercel/node';
import { getHealthStatus } from '../lib/health';

export default function handler(req: VercelRequest, res: VercelResponse) {
  res.status(200).json(getHealthStatus());
}
