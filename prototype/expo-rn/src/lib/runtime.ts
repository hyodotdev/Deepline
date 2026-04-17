import Constants from 'expo-constants';

const PRODUCTION_APP_ENVS = new Set(['prod', 'production']);

export function getAppEnv(): string {
  const appEnv =
    String(Constants.expoConfig?.extra?.appEnv ?? process.env.EXPO_PUBLIC_APP_ENV ?? 'local');

  return appEnv.toLowerCase();
}

export function isProductionAppEnv(): boolean {
  return PRODUCTION_APP_ENVS.has(getAppEnv());
}

export function isProductionCryptoBlocked(isProviderReady: boolean): boolean {
  return isProductionAppEnv() && !isProviderReady;
}
