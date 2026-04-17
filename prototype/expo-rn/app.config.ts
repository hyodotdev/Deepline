import 'dotenv/config';

import type {ConfigContext, ExpoConfig} from '@expo/config';
import dotenv from 'dotenv';
import {expand} from 'dotenv-expand';
import path from 'path';

import {version} from './package.json';

if (process.env.STAGE) {
  expand(
    dotenv.config({
      path: path.join(
        __dirname,
        ['./.env', process.env.STAGE].filter(Boolean).join('.'),
      ),
      override: true,
    }),
  );
}

export default ({config}: ConfigContext): ExpoConfig => ({
  ...config,
  name: 'Deepline',
  slug: 'deepline',
  scheme: 'deepline',
  version,
  orientation: 'portrait',
  icon: './assets/icon.png',
  userInterfaceStyle: 'automatic',
  assetBundlePatterns: ['**/*'],
  experiments: {
    typedRoutes: true,
  },
  plugins: [
    'expo-router',
    'expo-secure-store',
    'expo-camera',
    [
      'expo-splash-screen',
      {
        image: './assets/splash-icon.png',
        backgroundColor: '#0B67B2',
        imageWidth: 220,
      },
    ],
    [
      'expo-font',
      {
        fonts: [],
      },
    ],
  ],
  extra: {
    appEnv: process.env.EXPO_PUBLIC_APP_ENV ?? 'local',
    convexUrl: process.env.EXPO_PUBLIC_CONVEX_URL ?? '',
  },
  ios: {
    bundleIdentifier: 'dev.hyo.deepline',
    supportsTablet: false,
    infoPlist: {
      ITSAppUsesNonExemptEncryption: false,
      NSCameraUsageDescription:
        'Deepline uses the camera to scan contact invite QR codes.',
    },
  },
  android: {
    package: 'dev.hyo.deepline',
    adaptiveIcon: {
      foregroundImage: './assets/adaptive-icon.png',
      backgroundColor: '#0B67B2',
    },
    permissions: ['CAMERA'],
  },
  web: {
    bundler: 'metro',
    favicon: './assets/favicon.png',
  },
});
