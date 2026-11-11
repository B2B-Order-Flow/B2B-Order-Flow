import LogoutIcon from '@mui/icons-material/Logout';
import NotificationsNoneIcon from '@mui/icons-material/NotificationsNone';
import Alert from '@mui/material/Alert';
import AppBar from '@mui/material/AppBar';
import Box from '@mui/material/Box';
import Chip from '@mui/material/Chip';
import Drawer from '@mui/material/Drawer';
import IconButton from '@mui/material/IconButton';
import List from '@mui/material/List';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemText from '@mui/material/ListItemText';
import Stack from '@mui/material/Stack';
import Toolbar from '@mui/material/Toolbar';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import { Link as RouterLink, NavLink, Outlet } from 'react-router';
import { canAccess, homePath, isCompanyRole, menuFor, routeAccess } from '../../session/roles';
import { useSession } from '../../session/SessionProvider';
import { texts } from '../../texts/ru';
import { RoleSwitcher } from './RoleSwitcher';

const DRAWER_WIDTH = 232;

// Каркас приложения: шапка и меню по активной роли (ui-requirements.md §3.1–3.2).
export function AppLayout() {
  const { user, company, activeRole, logout } = useSession();
  const menu = menuFor(activeRole);
  const showCompany = company && (activeRole === null || isCompanyRole(activeRole));

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh' }}>
      <AppBar position="fixed" sx={{ zIndex: (theme) => theme.zIndex.drawer + 1 }}>
        <Toolbar sx={{ gap: 2 }}>
          <Typography
            variant="h6"
            component={RouterLink}
            to={homePath(activeRole)}
            sx={{ color: 'inherit', textDecoration: 'none', fontWeight: 700, flexShrink: 0 }}
          >
            {texts.app.name}
          </Typography>

          {showCompany ? (
            <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }} data-testid="header-company">
              <Typography variant="body2">{company.name}</Typography>
              {company.status !== 'ACTIVE' ? (
                <Chip
                  size="small"
                  color={company.status === 'PENDING' ? 'warning' : 'error'}
                  label={texts.companyStatus[company.status]}
                />
              ) : null}
            </Stack>
          ) : null}

          <Box sx={{ flexGrow: 1 }} />

          <RoleSwitcher />

          {canAccess(routeAccess.notifications, activeRole) ? (
            <Tooltip title={texts.menu.notifications}>
              <IconButton
                color="inherit"
                component={RouterLink}
                to="/notifications"
                aria-label={texts.menu.notifications}
                data-testid="header-notifications"
              >
                <NotificationsNoneIcon />
              </IconButton>
            </Tooltip>
          ) : null}

          <Stack sx={{ textAlign: 'right' }}>
            <Typography variant="body2" sx={{ fontWeight: 600, lineHeight: 1.2 }}>
              {user?.fullName}
            </Typography>
            <Typography variant="caption" sx={{ opacity: 0.8, lineHeight: 1.2 }}>
              {user?.login}
            </Typography>
          </Stack>
          <Tooltip title={texts.session.logout}>
            <IconButton color="inherit" onClick={() => logout()} aria-label={texts.session.logout} data-testid="logout">
              <LogoutIcon />
            </IconButton>
          </Tooltip>
        </Toolbar>
      </AppBar>

      <Drawer
        variant="permanent"
        sx={{
          width: DRAWER_WIDTH,
          flexShrink: 0,
          '& .MuiDrawer-paper': { width: DRAWER_WIDTH, boxSizing: 'border-box' },
        }}
      >
        <Toolbar />
        <List component="nav" data-testid="main-menu">
          {menu.map((item) => (
            <ListItemButton
              key={item.path}
              component={NavLink}
              to={item.path}
              end
              sx={{ '&.active': { bgcolor: 'action.selected', fontWeight: 600 } }}
            >
              <ListItemText primary={item.label} />
            </ListItemButton>
          ))}
        </List>
      </Drawer>

      <Box component="main" sx={{ flexGrow: 1, p: 3, minWidth: 0 }}>
        <Toolbar />
        {company?.status === 'PENDING' && isCompanyRole(activeRole) ? (
          <Alert severity="warning" sx={{ mb: 2 }} data-testid="company-pending">
            {texts.session.companyPending}
          </Alert>
        ) : null}
        <Outlet />
      </Box>
    </Box>
  );
}
