import '@emotion/react';

import type {AppTheme} from './theme/theme';

declare module '@emotion/react' {
  export interface Theme extends AppTheme {}
}
