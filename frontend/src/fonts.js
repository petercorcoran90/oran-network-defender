// Self-hosted fonts (bundled by Vite, served same-origin) — replaces the external Google Fonts
// <link>. This removes the third-party dependency (clearing the Sub-Resource-Integrity finding)
// and lets the CSP use `font-src 'self'` with no external font domains.
// Weights mirror what the old Google Fonts link requested.
import '@fontsource/chakra-petch/500.css';
import '@fontsource/chakra-petch/600.css';
import '@fontsource/chakra-petch/700.css';
import '@fontsource/jetbrains-mono/400.css';
import '@fontsource/jetbrains-mono/500.css';
import '@fontsource/jetbrains-mono/700.css';
import '@fontsource/oxanium/500.css';
import '@fontsource/oxanium/600.css';
import '@fontsource/oxanium/700.css';
import '@fontsource/ibm-plex-mono/400.css';
import '@fontsource/ibm-plex-mono/500.css';
import '@fontsource/ibm-plex-mono/600.css';
import '@fontsource/space-mono/400.css';
import '@fontsource/space-mono/700.css';
