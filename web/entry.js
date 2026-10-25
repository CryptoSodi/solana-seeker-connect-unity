import { createNostrSeekerLink } from '@solana-mobile/seeker-connect-web';
import { createBridge } from './bridge.js';
globalThis.UnitySeekerConnect = createBridge(createNostrSeekerLink(), globalThis.localStorage);
