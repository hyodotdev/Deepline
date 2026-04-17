import {useTheme} from '@emotion/react';

const spacing = {
  xs: 4,
  sm: 8,
  md: 12,
  lg: 16,
  xl: 20,
  xxl: 28,
};

const radius = {
  sm: 10,
  md: 16,
  lg: 22,
  pill: 999,
};

type ThemeColors = {
  accent: string;
  background: string;
  border: string;
  borderStrong: string;
  danger: string;
  pillDangerBg: string;
  pillDangerText: string;
  pillDefaultBg: string;
  pillDefaultText: string;
  pillSuccessBg: string;
  pillSuccessText: string;
  pillWarningBg: string;
  pillWarningText: string;
  placeholder: string;
  primary: string;
  primaryDark: string;
  qrBackground: string;
  qrForeground: string;
  success: string;
  surface: string;
  surfaceMuted: string;
  surfaceStrong: string;
  text: string;
  textInverse: string;
  textMuted: string;
};

export type AppTheme = {
  color: ThemeColors;
  name: 'light' | 'dark';
  radius: typeof radius;
  spacing: typeof spacing;
  statusBarStyle: 'light' | 'dark';
};

export const lightTheme: AppTheme = {
  name: 'light',
  statusBarStyle: 'dark',
  color: {
    background: '#EDF5FB',
    surface: '#F9FCFF',
    surfaceMuted: '#D8E8F7',
    surfaceStrong: '#0B67B2',
    border: '#C8DAEA',
    borderStrong: '#8FB2D1',
    text: '#10263C',
    textMuted: '#5C738B',
    textInverse: '#F4FAFF',
    primary: '#0B67B2',
    primaryDark: '#084C82',
    accent: '#58AFE7',
    danger: '#C25151',
    success: '#2A8D73',
    placeholder: '#7891A8',
    pillDefaultBg: '#DBEAF7',
    pillDefaultText: '#204B70',
    pillSuccessBg: '#DAF2E8',
    pillSuccessText: '#1F6E59',
    pillWarningBg: '#F4E5BE',
    pillWarningText: '#8C6111',
    pillDangerBg: '#F5D9DA',
    pillDangerText: '#93383E',
    qrBackground: '#F9FCFF',
    qrForeground: '#10385D',
  },
  spacing,
  radius,
};

export const darkTheme: AppTheme = {
  name: 'dark',
  statusBarStyle: 'light',
  color: {
    background: '#071523',
    surface: '#0D2237',
    surfaceMuted: '#14314B',
    surfaceStrong: '#1B86DF',
    border: '#1D3E5F',
    borderStrong: '#416B92',
    text: '#EAF4FE',
    textMuted: '#98B2C9',
    textInverse: '#F5FBFF',
    primary: '#2A90E0',
    primaryDark: '#1978C2',
    accent: '#6BC8FF',
    danger: '#F08C86',
    success: '#5CC2A4',
    placeholder: '#7D97B2',
    pillDefaultBg: '#15324D',
    pillDefaultText: '#B1CFE9',
    pillSuccessBg: '#163D37',
    pillSuccessText: '#8DE1C6',
    pillWarningBg: '#4A3714',
    pillWarningText: '#EEC86D',
    pillDangerBg: '#4A2429',
    pillDangerText: '#F1A4AD',
    qrBackground: '#F2F8FE',
    qrForeground: '#0E2740',
  },
  spacing,
  radius,
};

export function useAppTheme() {
  return useTheme() as AppTheme;
}
