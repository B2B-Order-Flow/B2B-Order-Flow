import Box from '@mui/material/Box';
import ButtonBase from '@mui/material/ButtonBase';
import { Link as RouterLink, NavLink, Outlet } from 'react-router';
import { homePath, isCompanyRole, menuFor } from '../../session/roles';
import { useSession } from '../../session/SessionProvider';
import { texts } from '../../texts/ru';
import { c, CONTENT_WIDTH, radius, textStyles } from '../../theme/tokens';
import { Brand } from '../ui/Brand';
import { Notice } from '../ui/Notice';
import { RoleSwitcher } from './RoleSwitcher';
import { UserMenu } from './UserMenu';

/** Ширина содержимого 1200 px и поля по бокам, как в макете 1440 px. */
export const contentBoxSx = { width: '100%', maxWidth: CONTENT_WIDTH + 48, mx: 'auto', px: '24px' } as const;

// Каркас по макетам: шапка 68 px — логотип, меню-«таблетки», роль, аватар (ui-requirements.md §3.1–3.2).
export function AppLayout() {
  const { company, activeRole } = useSession();
  const menu = menuFor(activeRole);

  return (
    <Box sx={{ minHeight: '100vh', bgcolor: c('bg-page') }}>
      <Box
        component="header"
        sx={{ height: 68, bgcolor: c('bg-surface'), borderBottom: `1px solid ${c('border-default')}` }}
      >
        <Box sx={{ ...contentBoxSx, height: '100%', display: 'flex', alignItems: 'center', gap: '24px' }}>
          <ButtonBase
            component={RouterLink}
            to={homePath(activeRole)}
            aria-label={texts.app.home}
            sx={{ borderRadius: `${radius.sm}px` }}
          >
            <Brand />
          </ButtonBase>

          <Box component="nav" data-testid="main-menu" sx={{ display: 'flex', gap: '4px', flex: 1, minWidth: 0 }}>
            {menu.map((item) => (
              <ButtonBase
                key={item.path}
                component={NavLink}
                to={item.path}
                end
                sx={{
                  ...textStyles.body,
                  px: '14px',
                  py: '8px',
                  borderRadius: `${radius.full}px`,
                  color: c('text-primary'),
                  whiteSpace: 'nowrap',
                  '&:hover': { bgcolor: c('bg-surface-2') },
                  '&.active': {
                    ...textStyles.accent,
                    bgcolor: c('action-primary-soft'),
                    color: c('action-primary-text'),
                  },
                }}
              >
                {item.label}
              </ButtonBase>
            ))}
          </Box>

          <RoleSwitcher />
          <UserMenu />
        </Box>
      </Box>

      <Box component="main" sx={contentBoxSx}>
        {company?.status === 'PENDING' && isCompanyRole(activeRole) ? (
          <Box sx={{ pt: '24px' }}>
            <Notice tone="waiting" testId="company-pending">
              {texts.session.companyPending}
            </Notice>
          </Box>
        ) : null}
        <Outlet />
      </Box>
    </Box>
  );
}
