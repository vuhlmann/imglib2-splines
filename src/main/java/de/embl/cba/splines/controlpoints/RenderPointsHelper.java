package de.embl.cba.splines.controlpoints;

import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform3D;

import java.awt.geom.GeneralPath;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * Helper for rendering overlay boxes.
 *
 * @author Stephan Saalfeld
 * @author Tobias Pietzsch
 * @author Jean-Yves Tinevez
 *
 */
public final class RenderPointsHelper
{
	/**
	 * distance from the eye to the projection plane z=0.
	 */
	private double depth = 10.0;

	/**
	 * scale the 2D projection of the overlay box by this factor.
	 */
	private double scale = 0.1;

	private final double[] origin = new double[ 3 ];

	private boolean perspective = false;

	private final List< double[] > intersectionPoints = new ArrayList<>();

	final static int numPoints = 8;

	final double[][] transformedPoints = new double[ numPoints ][ 3 ];

	final double[][] projectedPoints = new double[ numPoints ][ 2 ];

	public void setPerspectiveProjection( final boolean b )
	{
		perspective = b;
	}

	public void setScale( final double scale )
	{
		this.scale = scale;
	}

	public void setDepth( final double depth )
	{
		this.depth = depth;
		origin[ 2 ] = -depth;
	}

	public void setOrigin( final double x, final double y )
	{
		origin[ 0 ] = x;
		origin[ 1 ] = y;
	}

	/**
	 * Project a point.
	 *
	 * @param point
	 *            point to project
	 * @param projection
	 *            projected point is stored here
	 */
	public void project( final double[] point, final double[] projection )
	{
		final double f = perspective
				? scale * depth / ( point[ 2 ] - origin[ 2 ] )
				: scale;
		projection[ 0 ] = ( point[ 0 ] - origin[ 0 ] ) * f + origin[ 0 ];
		projection[ 1 ] = ( point[ 1 ] - origin[ 1 ] ) * f + origin[ 1 ];
	}

	/**
	 * Project a point.
	 *
	 * @param point
	 *            point to project
	 * @return projected point
	 */
	public double[] project( final double[] point )
	{
		final double[] projection = new double[ 2 ];
		project( point, projection );
		return projection;
	}

	/**
	 * Reproject a point
	 *
	 * @param x
	 *            projected x
	 * @param y
	 *            projected y
	 * @param z
	 *            z plane to which to reproject
	 * @return reprojected point
	 */
	public double[] reproject( final double x, final double y, final double z )
	{
		final double[] point = new double[ 3 ];
		final double f = perspective
				? ( z - origin[ 2 ] ) / ( scale * depth )
				: 1. / scale;
		point[ 0 ] = ( x - origin[ 0 ] ) * f + origin[ 0 ];
		point[ 1 ] = ( y - origin[ 1 ] ) * f + origin[ 1 ];
		point[ 2 ] = z;
		return point;
	}

	private void splitEdge( final int ia, final int ib, final GeneralPath before, final GeneralPath behind )
	{
		final double[] a = transformedPoints[ ia ];
		final double[] b = transformedPoints[ ib ];
		final double[] pa = projectedPoints[ ia ];
		final double[] pb = projectedPoints[ ib ];
		if ( a[ 2 ] <= 0 )
		{
			before.moveTo( pa[ 0 ], pa[ 1 ] );
			if ( b[ 2 ] <= 0 )
				before.lineTo( pb[ 0 ], pb[ 1 ] );
			else
			{
				final double[] t = new double[ 3 ];
				final double d = a[ 2 ] / ( a[ 2 ] - b[ 2 ] );
				t[ 0 ] = ( b[ 0 ] - a[ 0 ] ) * d + a[ 0 ];
				t[ 1 ] = ( b[ 1 ] - a[ 1 ] ) * d + a[ 1 ];
				final double[] pt = project( t );
				before.lineTo( pt[ 0 ], pt[ 1 ] );
				behind.moveTo( pt[ 0 ], pt[ 1 ] );
				behind.lineTo( pb[ 0 ], pb[ 1 ] );
				intersectionPoints.add( new double[] { pt[ 0 ], pt[ 1 ] } );
			}
		}
		else
		{
			behind.moveTo( pa[ 0 ], pa[ 1 ] );
			if ( b[ 2 ] > 0 )
				behind.lineTo( pb[ 0 ], pb[ 1 ] );
			else
			{
				final double[] t = new double[ 3 ];
				final double d = a[ 2 ] / ( a[ 2 ] - b[ 2 ] );
				t[ 0 ] = ( b[ 0 ] - a[ 0 ] ) * d + a[ 0 ];
				t[ 1 ] = ( b[ 1 ] - a[ 1 ] ) * d + a[ 1 ];
				final double[] pt = project( t );
				behind.lineTo( pt[ 0 ], pt[ 1 ] );
				before.moveTo( pt[ 0 ], pt[ 1 ] );
				before.lineTo( pb[ 0 ], pb[ 1 ] );
				intersectionPoints.add( new double[] { pt[ 0 ], pt[ 1 ] } );
			}
		}
	}

	public void renderPoints(
			final List< RealPoint > points,
			final AffineTransform3D transform,
			final GeneralPath front,
			final GeneralPath back,
			final GeneralPath intersection )
	{
		for ( int i = 0; i < numPoints; ++i )
		{
			final double[] position = new double[ 3 ];
			for ( int d = 0; d < 3; d++ )
				position[ d ] = points.get( i ).getDoublePosition( d );

			transform.apply( position, transformedPoints[ i ] );
			project( transformedPoints[ i ], projectedPoints[ i ] );
		}

		intersectionPoints.clear();

		// TODO: ????
		splitEdge( 0, 1, front, back );
		splitEdge( 2, 3, front, back );
		splitEdge( 4, 5, front, back );
		splitEdge( 6, 7, front, back );
		splitEdge( 0, 2, front, back );
		splitEdge( 1, 3, front, back );
		splitEdge( 4, 6, front, back );
		splitEdge( 5, 7, front, back );
		splitEdge( 0, 4, front, back );
		splitEdge( 1, 5, front, back );
		splitEdge( 2, 6, front, back );
		splitEdge( 3, 7, front, back );

		if ( intersectionPoints.size() > 2 )
		{
			final double x0 = intersectionPoints.stream()
					.mapToDouble( e -> e[ 0 ] )
					.average()
					.getAsDouble();
			final double y0 = intersectionPoints.stream()
					.mapToDouble( e -> e[ 1 ] )
					.average()
					.getAsDouble();
			intersectionPoints.sort( new PolarOrder( new double[] { x0, y0 } ) );
			final Iterator< double[] > hull = intersectionPoints.iterator();
			if ( hull.hasNext() )
			{
				final double[] first = hull.next();
				intersection.moveTo( first[ 0 ], first[ 1 ] );
				while ( hull.hasNext() )
				{
					final double[] next = hull.next();
					intersection.lineTo( next[ 0 ], next[ 1 ] );
				}
				intersection.closePath();
			}
		}
	}

	private static final class PolarOrder implements Comparator< double[] >
	{

		private final double[] p;

		public PolarOrder( final double[] p )
		{
			this.p = p;
		}

		@Override
		public int compare( final double[] q1, final double[] q2 )
		{
			final double dx1 = q1[ 0 ] - p[ 0 ];
			final double dy1 = q1[ 1 ] - p[ 1 ];
			final double dx2 = q2[ 0 ] - p[ 0 ];
			final double dy2 = q2[ 1 ] - p[ 1 ];

			if ( dy1 >= 0 && dy2 < 0 )
				return -1; // q1 above; q2 below
			else if ( dy2 >= 0 && dy1 < 0 )
				return +1; // q1 below; q2 above
			else if ( dy1 == 0 && dy2 == 0 )
			{ // 3-collinear and horizontal
				if ( dx1 >= 0 && dx2 < 0 )
					return -1;
				else if ( dx2 >= 0 && dx1 < 0 )
					return +1;
				else
					return 0;
			}
			else
				return -ccw( p, q1, q2 );
		}

		/**
		 * Returns true if a→b→c is a counterclockwise turn.
		 *
		 * @param a
		 *            first point
		 * @param b
		 *            second point
		 * @param c
		 *            third point
		 * @return { -1, 0, +1 } if a→b→c is a { clockwise, collinear;
		 *         counterclocwise } turn.
		 */
		private static int ccw( final double[] a, final double[] b, final double[] c )
		{
			final double area2 = ( b[ 0 ] - a[ 0 ] ) * ( c[ 1 ] - a[ 1 ] ) - ( b[ 1 ] - a[ 1 ] ) * ( c[ 0 ] - a[ 0 ] );
			if ( area2 < 0 )
				return -1;
			else if ( area2 > 0 )
				return +1;
			else
				return 0;
		}
	}
}
