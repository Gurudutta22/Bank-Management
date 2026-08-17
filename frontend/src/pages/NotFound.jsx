import { Link } from 'react-router-dom'
import { Compass } from 'lucide-react'
import { Button } from '../components/ui'

export default function NotFound() {
  return (
    <div className="grid min-h-screen place-items-center px-6">
      <div className="text-center animate-fade-up">
        <span className="mx-auto grid h-16 w-16 place-items-center rounded-2xl bg-[color:var(--accent-positive-chip)] text-[color:var(--accent-positive)]">
          <Compass size={28} />
        </span>
        <p className="mt-6 text-6xl font-bold tracking-tight brand-text-gradient">404</p>
        <h1 className="mt-3 text-xl font-semibold">This page does not exist</h1>
        <p className="mt-2 max-w-sm text-sm text-secondary">
          The link may be broken, or the page may have moved.
        </p>
        <Link to="/dashboard" className="mt-6 inline-block">
          <Button>Back to dashboard</Button>
        </Link>
      </div>
    </div>
  )
}
