/* SPDX-License-Identifier: AGPL-3.0-only */

/**
 * The one place torus geometry is defined. Everything else — blocks, entities, chunks, packets,
 * pathing, mod compatibility — asks {@link com.planetworld.wrap.core.DimensionTransformer} rather
 * than reimplementing a distance or a wrap.
 *
 * <h2>Coordinate invariants</h2>
 * <ul>
 *   <li><b>Server positions are wrapped.</b> Every {@code ChunkPos}, {@code SectionPos} and
 *       {@code BlockPos} the server stores, tickets, generates or keys a map by lies inside the
 *       domain. The choke points that enforce it are {@code ServerChunkCache} for loading and
 *       generation and {@code DistanceManager} / {@code TickingTracker} for tickets, so a caller
 *       that computes a position by walking off the edge of the world — a path region, a Create
 *       track lookahead, a Sable physics prediction box — resolves to the real chunk instead of
 *       generating a second copy of the world outside the bound.</li>
 *   <li><b>Client positions are continuous.</b> The client is never told about the seam: chunk and
 *       entity packets are remapped against the player's continuous {@code clientX}/{@code clientZ}
 *       so motion, cameras and interpolation never jump. Unwrapping exists for the packet layer and
 *       for rendering, not for server state.</li>
 *   <li><b>Wrapping is a change of frame, not a teleport.</b> Whoever moves a position across a
 *       bound also shifts what was derived from it — previous position, path nodes, anchors — by the
 *       same amount, so per-tick deltas keep measuring the short path.</li>
 *   <li><b>Distances take the short way around.</b> Any comparison of two absolute positions is
 *       either measured with this package's shortest-path helpers or performed after one operand has
 *       been unwrapped onto the other's frame. A raw subtraction of two wrapped positions is a bug
 *       whose value is roughly the width of the world.</li>
 *   <li><b>Project, then measure</b> when another mod owns a parallel coordinate frame (Sable
 *       sub-levels). {@link WorldSpaceProjector} is identity until a compat layer registers; core
 *       mixins never name that mod.</li>
 * </ul>
 */
package com.planetworld.wrap.core;
